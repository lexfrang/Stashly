package com.stashly.stashly;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface GroqService {
    @POST("v1/chat/completions")
    Call<GroqResponse> generateContent(@Header("Authorization") String apiKey, @Body GroqRequest request);

    class GroqRequest {
        public String model;
        public List<Message> messages;
        public ResponseFormat response_format;

        public GroqRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
            this.response_format = new ResponseFormat("json_object");
        }

        public static class Message {
            public String role;
            public List<Content> content;

            public Message(String role, List<Content> content) {
                this.role = role;
                this.content = content;
            }
        }

        public static class Content {
            public String type;
            public String text;
            public ImageUrl image_url;

            public Content(String type, String text) {
                this.type = type;
                this.text = text;
            }

            public Content(String type, ImageUrl imageUrl) {
                this.type = type;
                this.image_url = imageUrl;
            }
        }

        public static class ImageUrl {
            public String url;

            public ImageUrl(String url) {
                this.url = url;
            }
        }

        public static class ResponseFormat {
            public String type;
            public ResponseFormat(String type) {
                this.type = type;
            }
        }
    }

    class GroqResponse {
        public List<Choice> choices;

        public static class Choice {
            public Message message;
        }

        public static class Message {
            public String content;
        }
    }
}
