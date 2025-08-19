
package com.FinoStep.Fin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

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
            // Construct a new, detailed prompt to guide the AI's behavior
            String fullPrompt = "You are Megan, a financial advisor of FinoStep. Your expertise is strictly in finance and money-related topics. " +
                                "Respond to questions in a short and easy-to-understand language. " +
                                "If the user asks for difference give the response in tabular form. " +
                                "If a user asks a question that is not related to finance, politely decline and state that you are a financial advisor who can only help with money-related questions. " +
                                "Do not answer non-financial questions. Here is the user's message: " + userMessage;

            // Build the JSON payload with the new, customized prompt
            ObjectNode rootNode = objectMapper.createObjectNode();
            ArrayNode contentsNode = rootNode.putArray("contents");
            ObjectNode userContentNode = contentsNode.addObject();
            userContentNode.put("role", "user");
            ArrayNode partsArray = userContentNode.putArray("parts");
            ObjectNode textPart = partsArray.addObject();
            textPart.put("text", fullPrompt); // Use the fullPrompt here instead of userMessage

            // Create the HTTP request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_API_URL + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(rootNode)))
                    .build();

            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Check for successful response
            if (response.statusCode() == 200) {
                // Parse the Gemini API response
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

    /**
     * Handles audio uploads from the microphone button.
     * Note: This is a placeholder. It confirms audio receipt but does not perform
     * speech-to-text conversion, as that requires a separate service.
     */
    @PostMapping("/upload_audio")
    @ResponseBody
    public Map<String, String> uploadAudio(@RequestParam("file") MultipartFile file) {
        try {
            // Validate audio file
            if (file.isEmpty()) {
                return Collections.singletonMap("reply", "Audio file is empty.");
            }

            
            String transcribedText = speechToTextService.convertAudioToText(file.getBytes());
            
            if (transcribedText.isEmpty()) {
                return Collections.singletonMap("reply", "No speech detected in the audio.");
            }

            
            return chat(Collections.singletonMap("message", transcribedText));

        } catch (IOException e) {
            System.err.println("Speech-to-Text Error: " + e.getMessage());
            return Collections.singletonMap("reply", "Error converting audio to text.");
        } catch (Exception e) {
            System.err.println("Unexpected Error: " + e.getMessage());
            return Collections.singletonMap("reply", "An internal error occurred.");
        }
    }

    private static class speechToTextService {
        public static String convertAudioToText(byte[] audioBytes) throws IOException {
            try (SpeechClient speechClient = SpeechClient.create()) {
                ByteString audioData = ByteString.copyFrom(audioBytes);

                // Configure the recognition. Note: LINEAR16 is for raw, uncompressed WAV.
                RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setLanguageCode("en-US")
                    .build();

                RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(audioData)
                    .build();

                // Send to Google Speech API
                RecognizeResponse response = speechClient.recognize(config, audio);

                StringBuilder transcript = new StringBuilder();
                for (SpeechRecognitionResult result : response.getResultsList()) {
                    transcript.append(result.getAlternatives(0).getTranscript()).append(" ");
                }

                return transcript.toString().trim();
            }
        }
    }
}