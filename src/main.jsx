import React from 'react'
import ReactDOM from 'react-dom/client'
import App from '@/App.jsx'
import '@/index.css'

// Migrate accounts saved with the old provider host to the new one
try {
  const saved = JSON.parse(localStorage.getItem('playlists') || '[]');
  if (saved.some((p) => p.host && p.host.includes('hushvipnew.ink'))) {
    const migrated = saved.map((p) =>
      p.host && (p.host.includes('hushvipnew.ink') || p.host.includes('ottipdns.com') || (p.host.includes('accesshsh.online') && !p.host.includes(':443'))) ? { ...p, host: 'https://accesshsh.online:443' } : p
    );
    localStorage.setItem('playlists', JSON.stringify(migrated));
  }
} catch (e) {
  console.error('Playlist host migration failed', e);
}

ReactDOM.createRoot(document.getElementById('root')).render(
  // <React.StrictMode>
  <App />
  // </React.StrictMode>,
)

if (import.meta.hot) {
  import.meta.hot.on('vite:beforeUpdate', () => {
    window.parent?.postMessage({ type: 'sandbox:beforeUpdate' }, '*');
  });
  import.meta.hot.on('vite:afterUpdate', () => {
    window.parent?.postMessage({ type: 'sandbox:afterUpdate' }, '*');
  });
}