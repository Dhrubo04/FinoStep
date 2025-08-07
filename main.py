from fastapi import FastAPI, HTTPException, Request , UploadFile , File
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.middleware.cors import CORSMiddleware
import os
import google.generativeai as genai
import speech_recognition as sr  
import io
from pydub import AudioSegment



app = FastAPI()

origins = ["*"]  

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# app.mount("/templates", StaticFiles(directory="templates"), name="templates")
# templates = Jinja2Templates(directory="templates")
templates = Jinja2Templates(directory=".")
genai.configure(api_key="AIzaSyA1se35M8oYMigvXC9m1kxiW1xVJAwZ5WE") 
model = genai.GenerativeModel("gemini-1.5-flash")
generation_config = {
  "temperature": 0.1,
  "top_p": 0.95,
  "top_k": 40,
  "max_output_tokens": 100,
  "response_mime_type": "text/plain", 
}
chat_session = model.start_chat(
  history=[
    {
      "role": "system",
      "parts": [
        "You are Megan, a highly specialized financial assistant. You are programmed to only respond to questions and topics related to finance. If the user asks about anything unrelated to finance, kindly refuse to answer and say: 'I can only assist with finance-related questions.'"
      ]
    },
    {
      "role": "user",
      "parts": [
        "Hello, Megan! How do I start investing in the stock market?"
      ]
    },
    {
      "role": "model",
      "parts": [
        "Hi! I am Megan, your financial assistant. I can help you with investment advice, stock market queries, and more."
      ]
    },
  ]
)
def send_finance_question(question):
    """Send a finance question and return a finance-related answer only"""
    
    finance_keywords = ["invest", "stock", "financial", "tax", "budget", "savings", "market", "loan", "economy"]
    if not any(keyword in question.lower() for keyword in finance_keywords):
        return "Sorry, I can only assist with finance-related questions."

    response = chat_session.send_message(question)
    return response.text
UPLOAD_FOLDER = os.getenv("UPLOAD_FOLDER", "uploads")
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


r = sr.Recognizer()
@app.get("/")
async def root(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})

@app.get("/")
async def root(request: Request):
    return templates.TemplateResponse("bot.html", {"request": request})

@app.post("/chat")
async def chat(request: Request):
    
    data = await request.json()
    message = data.get("message")

    if not message:
        raise HTTPException(status_code=400, detail="No message provided")

    try:
        response = model.generate_content(message) 
        bot_reply = response.text
        return {"reply": bot_reply}
    except Exception as e:
        print(f"Error communicating with Gemini API: {e}")
        raise HTTPException(status_code=500, detail="Error communicating with Gemini API")
    
def upload_to_gemini(audio_content):
    """Uploads the given audio to Gemini."""
    file = genai.upload_file(io.BytesIO(audio_content), mime_type="audio/wav")
    print(f"Uploaded file '{file.display_name}' as: {file.uri}")
    return file
@app.post("/upload_audio")
async def upload_audio(file: UploadFile = File(...)):
    try:
        # Read the uploaded audio file
        audio_content = await file.read()
        print(f"Received audio file of size {len(audio_content)} bytes")
        
        if file.content_type != "audio/wav":
            raise HTTPException(status_code=400, detail="Invalid file format, expected audio/wav")

        audio_file = io.BytesIO(audio_content)
        audio = AudioSegment.from_file(audio_file, format="wav")
        
        print(f"Audio file loaded successfully. Duration: {audio.duration_seconds} seconds")
        
        wav_audio = io.BytesIO()
        audio.export(wav_audio, format="wav")
        wav_audio.seek(0)

        # Upload the audio file to Gemini
        uploaded_file = upload_to_gemini(wav_audio.read())

        # Perform speech recognition
        with sr.AudioFile(wav_audio) as source:
            audio_data = r.record(source)
        
        try:
            # Recognize the text from the audio
            message = r.recognize_google(audio_data)
            print(f"Recognized text: {message}")
            
            # Send message to Gemini model
            chat_session = model.start_chat(
                history=[
                    {
                        "role": "user",
                        "parts": [uploaded_file],
                    },
                    {
                        "role": "model",
                        "parts": [f"Received audio message: {message}\nIs there anything I can assist you with?"],
                    },
                ]
            )

            # Get and return the response from Gemini
            response = chat_session.send_message(message)
            return {"message": message, "gemini_response": response.text}

        except sr.UnknownValueError:
            print("Speech recognition could not understand the audio.")
            return {"message": "Could not understand the audio."}
        except sr.RequestError as e:
            print(f"Error with speech recognition service: {e}")
            return {"message": f"Error with the speech recognition service: {e}"}
    
    except Exception as e:
        print(f"Error processing audio: {e}")
        raise HTTPException(status_code=500, detail=f"Error processing audio: {e}")
