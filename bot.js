const chat = document.getElementById('chat');
const userInput = document.getElementById('userInput');
const sendButton = document.getElementById('sendButton');
const micInput = document.querySelector('.mic-input');
const recordingStatus = document.getElementById('recording-status');
let mediaRecorder = null;
let audioChunks = [];
let blinkInterval = null;

// Append message function
const appendMessage = (message, isUser) => { 
    const messageElement = document.createElement('div');
    messageElement.classList.add('message', isUser ? 'user' : 'bot');

    const profile = document.createElement('div');
    profile.classList.add('profile');

    const bubble = document.createElement('div');
    bubble.classList.add('bubble');

    // Render markdown tables if present
    if (message.includes('|') && message.includes('-')) {
        bubble.innerHTML = marked.parse(message);
    } else {
        bubble.textContent = message;
    }

    if (isUser) {
        messageElement.appendChild(bubble);
        messageElement.appendChild(profile);
    } else {
        messageElement.appendChild(profile);
        messageElement.appendChild(bubble);
    }

    chat.appendChild(messageElement);
    chat.scrollTop = chat.scrollHeight;
};

// Send message function
const sendMessage = async () => { 
    const message = userInput.value.trim();
    if (!message) return;

    appendMessage(message, true);
    userInput.value = '';

    try {
        const response = await fetch('/chat', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ message })
        });

        if (!response.ok) throw new Error('Network response was not ok');

        const data = await response.json();
        appendMessage(data.reply, false);
    } catch (error) {
        console.error('Error:', error);
        appendMessage('Hi! I am Megan. How can I help you with Finance?', false);
    }
};

// Event listeners for sending messages
sendButton.addEventListener('click', sendMessage);
userInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') sendMessage(); });

// Microphone recording
micInput.addEventListener('click', async () => {
    if (mediaRecorder && mediaRecorder.state === "recording") {
        mediaRecorder.stop();
        micInput.classList.remove('recording');
        clearInterval(blinkInterval);
        recordingStatus.textContent = '';
        return;
    }

    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
        audioChunks = [];

        mediaRecorder.ondataavailable = e => { if (e.data.size > 0) audioChunks.push(e.data); };

        mediaRecorder.onstop = async () => {
            clearInterval(blinkInterval);
            recordingStatus.textContent = '';

            const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
            const formData = new FormData();
            formData.append('file', audioBlob, 'mic_audio.webm');

            try {
        const response = await fetch('/upload_audio', { method: 'POST', body: formData });
        const data = await response.json();

        const transcribedText = data.transcribed || '';
        const botReply = data.botReply || '';

        if (transcribedText) appendMessage(transcribedText, true);
        if (botReply) appendMessage(botReply, false);

    } 
             catch (err) {
                console.error(err);
                appendMessage("Error sending voice message.", true);
            }

            audioChunks = [];
        };

        mediaRecorder.start();
        micInput.classList.add('recording');

        // Blink "Recording..." text
        let visible = true;
        blinkInterval = setInterval(() => { recordingStatus.textContent = visible ? 'Recording...' : ''; visible = !visible; }, 500);

    } catch (err) {
        console.error(err);
        appendMessage("Error accessing microphone.", true);
    }
});
// Helper to send transcribed text to bot
const sendMessageFromText = async (text) => {
    try {
        const response = await fetch('/chat', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ message: text })
        });

        if (!response.ok) throw new Error('Network response was not ok');
        const data = await response.json();
        appendMessage(data.reply, false);

    } catch (error) {
        console.error('Error:', error);
        appendMessage('Hi! I am Megan. How can I help you with Finance?', false);
    }
};
