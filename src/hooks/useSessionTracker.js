import { useEffect, useRef } from 'react';
import { trackSession } from '@/functions/trackSession';

let SESSION_ID = sessionStorage.getItem('htv_session_id');
if (!SESSION_ID) {
    SESSION_ID = 'sess_' + Math.random().toString(36).substr(2, 12) + '_' + Date.now();
    sessionStorage.setItem('htv_session_id', SESSION_ID);
}

/**
 * Tracks the current user session in the database.
 * Call this at the app level with current content info.
 */
export function useSessionTracker({ currentContent = '', contentType = 'idle' } = {}) {
    const intervalRef = useRef(null);

    useEffect(() => {
        const sendPing = async () => {
            try {
                // Get playlist info from localStorage
                const playlists = JSON.parse(localStorage.getItem('playlists') || '[]');
                const activePlaylist = playlists[0]; // Use first/active playlist

                // Detect device type from user agent
                const ua = navigator.userAgent;
                let device_type = 'desktop';
                if (/TV|SmartTV|WebOS|Tizen|SMART-TV/i.test(ua)) device_type = 'tv';
                else if (/Mobile|Android|iPhone/i.test(ua)) device_type = 'mobile';
                else if (/iPad|Tablet/i.test(ua)) device_type = 'tablet';

                await trackSession({
                    session_id: SESSION_ID,
                    account_name: activePlaylist?.label || activePlaylist?.username || 'Guest',
                    username: activePlaylist?.username || '',
                    device_type,
                    current_content: currentContent,
                    content_type: contentType,
                    playlist_host: activePlaylist?.host || '',
                    account_expiry: activePlaylist?.expiry || '',
                    max_connections: activePlaylist?.max_connections || '',
                    active_connections: activePlaylist?.active_connections || ''
                });
            } catch (e) {
                // Silently fail — don't disrupt user experience
            }
        };

        sendPing();
        intervalRef.current = setInterval(sendPing, 60000); // Ping every 60s

        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [currentContent, contentType]);
}

export { SESSION_ID };