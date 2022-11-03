/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.chatapp.service;

import com.chatapp.chat.ChatMessage;
import com.chatapp.chat.ChatServiceGrpc;
import com.chatapp.chat.GetChatRequest;
import com.chatapp.chat.MessageList;
import com.chatapp.chat.SendMessageRequest;
import com.chatapp.common.GetRequest;
import com.chatapp.common.ServiceResponse;
import com.chatapp.database.MySqlConnection;
import com.chatapp.grpcchatapp.JWToken;
import com.chatapp.grpcchatapp.MessageData;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author maria
 */
public class ChatService extends ChatServiceGrpc.ChatServiceImplBase {

    private HashMap<Integer, StreamObserver<ChatMessage>> onlineClients;

    public ChatService(HashMap<Integer, StreamObserver<ChatMessage>> onlineClients) {
        this.onlineClients = onlineClients;
    }

    @Override
    public void getMessages(GetChatRequest request, StreamObserver<MessageList> responseObserver) {
        JWToken token = new JWToken(request.getToken());
        MessageList.Builder response = MessageList.newBuilder();

        if (token.isValid()) {
            int userId = token.getUserId();
            int friendId = request.getFriend().getUserId();

            ChatMessage.Builder chatMessage = ChatMessage.newBuilder();

            MySqlConnection database = new MySqlConnection();

            try {
                ArrayList<MessageData> results = database.fetchMessages(userId, friendId);
                for (MessageData message : results) {

                    chatMessage.setSenderId(message.getSenderId())
                            .setMessage(message.getMessage())
                            .setTimestamp(message.getTimestamp())
                            .setSeen(message.isSeen());
                    response.addMessages(chatMessage.build());
                    chatMessage.clear();
                }

            } catch (Exception ex) {
                Logger.getLogger(ChatService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendMessage(SendMessageRequest request, StreamObserver<ServiceResponse> responseObserver) {
        JWToken token = new JWToken(request.getToken());
        ServiceResponse.Builder response = ServiceResponse.newBuilder();

        if (token.isValid()) {
            String message = request.getMessage();
            int userId = token.getUserId();
            int friendId = request.getReciever().getUserId();

            MySqlConnection database = new MySqlConnection();

            if (onlineClients.containsKey(friendId)) {
                
                ChatMessage.Builder chatMessage = ChatMessage.newBuilder();
                chatMessage.setSenderId(userId)
                            .setMessage(message)
                            .setTimestamp(Instant.now().toString())
                            .setSeen(false);
                onlineClients.get(friendId).onNext(chatMessage.build());
            }
            
            try {
                database.saveMsg(userId, friendId, message);

                response.setResponse("Message sent");
                response.setResponseCode(1);
            } catch (Exception ex) {
                Logger.getLogger(ChatService.class.getName()).log(Level.SEVERE, null, ex);
                response.setResponse("Internal error");
                response.setResponseCode(0);
            }
        } else {
            response.setResponse("Verification failed");
            response.setResponseCode(0);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void receiveMessage(GetRequest request, StreamObserver<ChatMessage> responseObserver) {

        JWToken token = new JWToken(request.getToken());
        if (token.isValid()) {
            int userId = token.getUserId();
            if (!onlineClients.containsKey(userId)) {
                System.out.println("added key " + userId);
                onlineClients.put(userId, responseObserver);
            }
        }
    }
}