package com.FinoStep.Fin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.OkHttpClient;

@Controller
public class BotController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-05-20:generateContent?key=";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/")
    public String botPage() {
        return "bot";
    }

    @PostMapping("/chat")
    @ResponseBody
    public Map<String, String> chat(@RequestBody Map<String, String> requestPayload) {
        String userMessage = requestPayload.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Collections.singletonMap("reply", "Please provide a message.");
        }

        try {
            String fullPrompt = "You are Megan, a financial advisor of FinoStep. Your expertise is strictly in finance and money-related topics. " +
                                "Respond to questions in a short and easy-to-understand language. " +
                                "If the user asks for difference give the response in tabular form. " +
                                "If a user asks a question that is not related to finance, politely decline and state that you are a financial advisor who can only help with money-related questions. " +
                                "Do not answer non-financial questions. Here is the user's message: " + userMessage;

            ObjectNode rootNode = objectMapper.createObjectNode();
            ArrayNode contentsNode = rootNode.putArray("contents");
            ObjectNode userContentNode = contentsNode.addObject();
            userContentNode.put("role", "user");
            ArrayNode partsArray = userContentNode.putArray("parts");
            ObjectNode textPart = partsArray.addObject();
            textPart.put("text", fullPrompt);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_API_URL + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(rootNode)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode geminiResponse = objectMapper.readTree(response.body());
                String botReply = geminiResponse.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
                return Collections.singletonMap("reply", botReply);
            } else {
                System.err.println("Gemini API Error: " + response.body());
                return Collections.singletonMap("reply", "Error communicating with the AI. Please try again.");
            }
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace();
            return Collections.singletonMap("reply", "An internal error occurred.");
        }
    }

    @PostMapping("/upload_audio")
    @ResponseBody
    public Map<String, String> uploadAudio(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Collections.singletonMap("reply", "Audio file is empty.");
            }

            String transcribedText = speechToTextService.convertAudioToText(file.getBytes(), file);

            if (transcribedText.isEmpty()) {
                return Collections.singletonMap("reply", "No speech detected in the audio.");
            }
             Map<String, String> botResponse = chat(Map.of("message", transcribedText));

            return Map.of(
            "transcribed", transcribedText,
            "botReply", botResponse.get("reply")
        );

    } catch (Exception e) {
        e.printStackTrace();
        return Map.of("transcribed", "", "botReply", "An internal error occurred.");
    }
}

    private static class speechToTextService {
        private static final String API_KEY = "571f2eea96c14a70b72947e682bab90b";
        private static final String UPLOAD_URL = "https://api.assemblyai.com/v2/upload";
        private static final String TRANSCRIBE_URL = "https://api.assemblyai.com/v2/transcript";

        private static final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        public static String convertAudioToText(byte[] audioBytes, MultipartFile file) throws Exception {
            // 1. Determine proper Content-Type
            String contentType = "audio/webm";

            if (file.getContentType() != null) {
                contentType = file.getContentType();
            }

            System.out.println("Uploading file: " + file.getOriginalFilename());
            System.out.println("Content-Type: " + contentType);
            System.out.println("Size: " + file.getSize());

            // 2. Upload audio
            // 2. Upload audio with dynamic MIME type
            

            okhttp3.RequestBody uploadBody = okhttp3.RequestBody.create(audioBytes, okhttp3.MediaType.parse(contentType));
            okhttp3.Request uploadRequest = new okhttp3.Request.Builder()
                    .url(UPLOAD_URL)
                    .addHeader("authorization", API_KEY)
                    .post(uploadBody)
                    .build();


            String uploadUrl;
            try (okhttp3.Response response = client.newCall(uploadRequest).execute()) {
                if (!response.isSuccessful())
                    throw new IOException("Upload failed: " + response);
                ObjectMapper mapper = new ObjectMapper();
                uploadUrl = mapper.readTree(response.body().string()).get("upload_url").asText();
            }

            // 3. Request transcription
            String json = "{ \"audio_url\": \"" + uploadUrl + "\" }";
            okhttp3.RequestBody transcribeBody = okhttp3.RequestBody.create(json, okhttp3.MediaType.parse("application/json"));
            okhttp3.Request transcribeRequest = new okhttp3.Request.Builder()
                    .url(TRANSCRIBE_URL)
                    .addHeader("authorization", API_KEY)
                    .post(transcribeBody)
                    .build();

            String transcriptId;
            try (okhttp3.Response response = client.newCall(transcribeRequest).execute()) {
                if (!response.isSuccessful())
                    throw new IOException("Transcription request failed: " + response);
                ObjectMapper mapper = new ObjectMapper();
                transcriptId = mapper.readTree(response.body().string()).get("id").asText();
            }

            // 4. Poll for result
            String status, text = "";
            while (true) {
                okhttp3.Request pollRequest = new okhttp3.Request.Builder()
                        .url(TRANSCRIBE_URL + "/" + transcriptId)
                        .addHeader("authorization", API_KEY)
                        .get()
                        .build();

                try (okhttp3.Response response = client.newCall(pollRequest).execute()) {
                    if (!response.isSuccessful())
                        throw new IOException("Polling failed: " + response);

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode result = mapper.readTree(response.body().string());
                    status = result.get("status").asText();

                    if ("completed".equals(status)) {
                        text = result.get("text").asText();
                        break;
                    } else if ("error".equals(status)) {
                        throw new IOException("Transcription failed: " + result.toString());
                    }
                }
                Thread.sleep(3000);
            }

            return text;
        }
    }
}
