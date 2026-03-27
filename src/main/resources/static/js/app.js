document.addEventListener('DOMContentLoaded', () => {

    const chatForm = document.getElementById('chatForm');
    const messageInput = document.getElementById('messageInput');
    const chatFeed = document.getElementById('chatFeed');
    const sendBtn = document.getElementById('sendBtn');
    
    const searchInput = document.getElementById('searchInput');
    const searchResults = document.getElementById('searchResults');
    const searchLoader = document.getElementById('searchLoader');

    // Estado local da conversa
    let history = [];
    
    // Auto-resize textarea
    messageInput.addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = (this.scrollHeight < 150 ? this.scrollHeight : 150) + 'px';
    });

    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            chatForm.dispatchEvent(new Event('submit'));
        }
    });

    chatForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const text = messageInput.value.trim();
        if (!text) return;

        // Limpar input
        messageInput.value = '';
        messageInput.style.height = 'auto';

        // Add user bubble
        appendUserMessage(text);
        
        // Disable UI
        sendBtn.disabled = true;

        // Add empty assistant bubble for streaming
        const bubble = createAssistantBubble();
        chatFeed.appendChild(bubble);
        scrollToBottom();

        const contentDiv = bubble.querySelector('.content');
        let fullResponse = "";

        try {
            // Chamada SSE via POST
            const response = await fetch('/api/v1/chat/stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: text, history: history })
            });

            if (!response.ok) throw new Error('Falha na resposta da API');

            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');

            // Renderização parcial com highlight cursor
            contentDiv.innerHTML = '<span class="streaming-cursor"></span>';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                const chunk = decoder.decode(value, { stream: true });
                const lines = chunk.split('\n');

                for (let line of lines) {
                    if (line.startsWith('data:')) {
                        const data = line.substring(5);
                        fullResponse += data;
                        
                        // Parse Markdown parcial
                        const htmlContent = marked.parse(fullResponse);
                        contentDiv.innerHTML = `<div class="markdown-body">${htmlContent}</div><span class="streaming-cursor"></span>`;
                        scrollToBottom();
                    }
                }
            }
            
            // Render final, removendo o cursor
            contentDiv.innerHTML = `<div class="markdown-body">${marked.parse(fullResponse)}</div>`;
            
            // Adiciona ao histórico do contexto
            history.push(["user", text]);
            history.push(["assistant", fullResponse]);

        } catch (error) {
            console.error('SSE Error:', error);
            contentDiv.innerHTML = `<div class="markdown-body"><p style="color: #ef4444;">Ocorreu um erro ao comunicar com a API.</p></div>`;
        } finally {
            sendBtn.disabled = false;
            messageInput.focus();
        }
    });

    function appendUserMessage(text) {
        const div = document.createElement('div');
        div.className = 'message user slide-up';
        div.innerHTML = `
            <div class="avatar user-avatar">
                <svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
            </div>
            <div class="content">${escapeHtml(text)}</div>
        `;
        chatFeed.appendChild(div);
        scrollToBottom();
    }

    function createAssistantBubble() {
        const div = document.createElement('div');
        div.className = 'message assistant slide-up';
        div.innerHTML = `
            <div class="avatar assistant-avatar">
                <svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>
            </div>
            <div class="content"></div>
        `;
        return div;
    }

    function scrollToBottom() {
        chatFeed.scrollTop = chatFeed.scrollHeight;
    }

    function escapeHtml(unsafe) {
        return unsafe
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    // --- Busca Semântica (Debounced) ---
    let searchTimeout;
    searchInput.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        const query = e.target.value.trim();
        
        if (!query) {
            searchResults.innerHTML = '<div class="empty-state">Digite algo para buscar no vault.</div>';
            return;
        }

        searchLoader.classList.remove('hidden');
        searchResults.innerHTML = '';

        searchTimeout = setTimeout(async () => {
            try {
                const res = await fetch(`/api/v1/search?q=${encodeURIComponent(query)}&limit=5`);
                if (!res.ok) throw new Error('Search failed');
                
                const results = await res.json();
                searchLoader.classList.add('hidden');
                
                if (results.length === 0) {
                    searchResults.innerHTML = '<div class="empty-state">Nada encontrado nesta galáxia...</div>';
                    return;
                }

                results.forEach((r, idx) => {
                    const card = document.createElement('div');
                    card.className = 'search-card';
                    card.style.animationDelay = \`\${idx * 0.05}s\`;
                    card.innerHTML = \`
                        <h4>\${r.title}</h4>
                        <span class="score">Score: \${r.score.toFixed(3)}</span>
                        <p>\${escapeHtml(r.excerpt)}</p>
                    \`;
                    searchResults.appendChild(card);
                });

            } catch (err) {
                searchLoader.classList.add('hidden');
                searchResults.innerHTML = '<div class="empty-state" style="color:#ef4444">Falha na busca semântica.</div>';
            }
        }, 500); // 500ms debounce
    });
});
