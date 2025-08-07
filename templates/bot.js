const chat = document.getElementById('chat');
const userInput = document.getElementById('userInput');
const sendButton = document.getElementById('sendButton');
const micInput = document.querySelector('.mic-input');
//image
// const imageUrls = document.getElementById('image-urls').dataset;
// const userImageUrl = imageUrls.userImage;
// const botImageUrl = imageUrls.botImage;
// const userImageUrl = document.getElementById('user-image').src;
// const botImageUrl = document.getElementById('bot-image').src;
// const languageDropdown = document.getElementById('language-dropdown');
// const audioBlob = new Blob(audioChunks, { type: 'audio/wav' });


const appendMessage = (message, isUser) => { 
    const messageElement = document.createElement('div');
    messageElement.classList.add('message', isUser ? 'user' : 'bot');

    const profile = document.createElement('div');
    profile.classList.add('profile');
//image
    // const img = document.createElement('img');
    // img.src = isUser ? userImageUrl : botImageUrl;
    // img.src = isUser 
    //     ? `{{ url_for('templates', path='/user.jpg') }}` 
    //     : `{{ url_for('templates', path='/megan-profile.jpg') }}`;
    // profile.appendChild(img);

    const bubble = document.createElement('div');
    bubble.classList.add('bubble');
    bubble.textContent = message;

    messageElement.appendChild(isUser ? bubble : profile);
    messageElement.appendChild(isUser ? profile : bubble);

    chat.appendChild(messageElement);
    chat.scrollTop = chat.scrollHeight;
};

const sendMessage = async () => { 
    const message = userInput.value;
    if (!message.trim()) return;

    appendMessage(message, true);
    userInput.value = '';

    try {
        const response = await fetch('/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ message })
        });

        if (!response.ok) {
            throw new Error('Network response was not ok');
        }

        const data = await response.json();
        appendMessage(data.reply, false);
    } catch (error) {
        console.error('Error:', error);
        appendMessage('Hi there! How can I help you today?', false);
    }
 };

sendButton.addEventListener('click', sendMessage);
userInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') sendMessage();
});

// micInput.addEventListener('click', () => { 
//     const voiceMessage = "This is a voice message."; 
//     appendMessage(voiceMessage, true); 
//  });
let mediaRecorder=null;
let audioChunks = [];

micInput.addEventListener('click', async () => {
    if (mediaRecorder && mediaRecorder.state === "recording") {
        mediaRecorder.stop();  
        micInput.classList.remove('recording'); 
        console.log("Recording stopped");
        return;
    }

    try {
        // Request access to the microphone
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        console.log("Microphone access granted");

        // Create a MediaRecorder instance for audio
        mediaRecorder = new MediaRecorder(stream);
        audioChunks = []; // Reset audio chunks on new recording

        // Collect audio data when available
        mediaRecorder.ondataavailable = event => {
            if (event.data.size > 0) {
                audioChunks.push(event.data);
            }
        };

        // Handle recording stop and send audio to the backend
        mediaRecorder.onstop = async () => {
            console.log("Recording completed, processing audio");
            const audioBlob = new Blob(audioChunks, { type: 'audio/wav' });
            const formData = new FormData();
            formData.append('file', audioBlob, 'audio.wav');

            try {
                const response = await fetch('/upload_audio', {
                    method: 'POST',
                    body: formData,
                });

                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }

                const data = await response.json();
                const message = data.message;

                if (message) {
                    appendMessage(message, true); // Display recognized text
                    sendMessage(message); // Send recognized text to API
                } else {
                    appendMessage('No text recognized from the audio.', true);
                }
            } catch (error) {
                console.error('Error:', error);
                appendMessage('Voice message sent', true);
            }

            audioChunks = [];
        };

        // Start recording
        mediaRecorder.start();
        micInput.classList.add('recording'); // Add recording style
        console.log("Recording started");
    } catch (error) {
        console.error("Error accessing microphone:", error);
        appendMessage("Error accessing the microphone.", true);
    }
});
//image
//  const userImageUrl = "{{ url_for('templates', path='/user.jpg') }}";
//  const botImageUrl = "{{ url_for('templates', path='/megan-profile.jpg') }}";
// languageDropdown.addEventListener('change', function () {
//     const selectedLanguage = languageDropdown.value;

    
//     alert(`You selected: ${selectedLanguage}`);
    
//     if (selectedLanguage === 'hi') {
//         console.log('Loading Hindi translations...');
//     } else if (selectedLanguage === 'bn') {
//         console.log('Loading Bangla translations...');
        
//     }
// });
