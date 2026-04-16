import { useEffect, useState, useRef } from 'react';
import { base44 } from '@/api/base44Client';

/**
 * Hook that polls for active broadcast messages and returns them.
 * Shows each message once per session using sessionStorage.
 */
export function useBroadcast() {
    const [activeBroadcasts, setActiveBroadcasts] = useState([]);
    const [newMessage, setNewMessage] = useState(null);
    const seenIds = useRef(new Set(JSON.parse(sessionStorage.getItem('seenBroadcasts') || '[]')));

    useEffect(() => {
        const check = async () => {
            try {
                const msgs = await base44.entities.BroadcastMessage.filter({ is_active: true }, '-created_date', 10);
                const now = new Date();
                const valid = msgs.filter(m => !m.expires_at || new Date(m.expires_at) > now);
                setActiveBroadcasts(valid);

                // Find any new ones we haven't shown yet
                const unseen = valid.find(m => !seenIds.current.has(m.id));
                if (unseen) {
                    setNewMessage(unseen);
                    seenIds.current.add(unseen.id);
                    sessionStorage.setItem('seenBroadcasts', JSON.stringify([...seenIds.current]));
                }
            } catch (e) {
                // Silently fail
            }
        };

        check();
        const interval = setInterval(check, 30000); // Poll every 30s
        return () => clearInterval(interval);
    }, []);

    const dismissMessage = () => setNewMessage(null);

    return { activeBroadcasts, newMessage, dismissMessage };
}