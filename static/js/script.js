const chat = document.getElementById('chat');
const userInput = document.getElementById('userInput');
const sendButton = document.getElementById('sendButton');
const micInput = document.querySelector('.mic-input');

const appendMessage = (message, isUser) => { /* ... (same as before) ... */ };

const sendMessage = async () => { /* ... (same as before) ... */ };

sendButton.addEventListener('click', sendMessage);
userInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') sendMessage();
});

micInput.addEventListener('click', () => { /* ... (same as before) ... */ });