package com.example.accidentdetection.network;

import com.example.accidentdetection.model.AuthResponse;
import com.example.accidentdetection.model.LoginRequest;
import com.example.accidentdetection.model.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("api/auth/register")
    Call<Void> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);
}
