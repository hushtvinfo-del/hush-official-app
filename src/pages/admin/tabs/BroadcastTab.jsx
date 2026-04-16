import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { adminAction } from '@/functions/adminAction';
import { Megaphone, Send, Trash2, EyeOff, AlertTriangle, Info, Bell, Wrench, Clock } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

const TYPE_COLORS = {
    info: 'bg-blue-500/20 text-blue-300 border-blue-500/40',
    warning: 'bg-yellow-500/20 text-yellow-300 border-yellow-500/40',
    alert: 'bg-red-500/20 text-red-300 border-red-500/40',
    maintenance: 'bg-orange-500/20 text-orange-300 border-orange-500/40',
};
const TYPE_ICONS = { info: Info, warning: AlertTriangle, alert: Bell, maintenance: Wrench };

export default function BroadcastTab({ broadcasts, adminToken, connectedCount, onRefresh }) {
    const [title, setTitle] = useState('');
    const [message, setMessage] = useState('');
    const [type, setType] = useState('info');
    const [expiresIn, setExpiresIn] = useState('');
    const [sending, setSending] = useState(false);
    const [sent, setSent] = useState(false);

    const send = async () => {
        if (!message.trim()) return;
        setSending(true);

        let expires_at = null;
        if (expiresIn) {
            const mins = parseInt(expiresIn);
            if (!isNaN(mins)) {
                expires_at = new Date(Date.now() + mins * 60 * 1000).toISOString();
            }
        }

        await adminAction({ admin_token: adminToken, action: 'broadcast', payload: { message, title, type, expires_at } });
        setMessage('');
        setTitle('');
        setExpiresIn('');
        setSending(false);
        setSent(true);
        setTimeout(() => setSent(false), 3000);
        onRefresh();
    };

    const deactivate = async (id) => {
        await adminAction({ admin_token: adminToken, action: 'deactivate_broadcast', payload: { id } });
        onRefresh();
    };

    const deleteBroadcast = async (id) => {
        await adminAction({ admin_token: adminToken, action: 'delete_broadcast', payload: { id } });
        onRefresh();
    };

    const activeBroadcasts = broadcasts.filter(b => b.is_active);

    return (
        <div className="space-y-6">
            {/* Compose */}
            <Card className="bg-slate-800/50 border-blue-500/30">
                <CardHeader className="pb-3">
                    <CardTitle className="text-white flex items-center gap-2">
                        <Megaphone className="w-5 h-5 text-cyan-400" />
                        Send Broadcast Message
                    </CardTitle>
                    <p className="text-sm text-gray-400">
                        Currently <span className="text-cyan-300 font-bold">{connectedCount}</span> user{connectedCount !== 1 ? 's' : ''} connected — all will see this message.
                    </p>
                </CardHeader>
                <CardContent className="space-y-4">
                    {/* Type selector */}
                    <div>
                        <label className="text-xs text-gray-400 mb-2 block">Message Type</label>
                        <div className="flex gap-2 flex-wrap">
                            {['info', 'warning', 'alert', 'maintenance'].map(t => {
                                const Icon = TYPE_ICONS[t];
                                return (
                                    <button
                                        key={t}
                                        onClick={() => setType(t)}
                                        className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-sm font-medium transition-all capitalize ${type === t ? TYPE_COLORS[t] + ' ring-1 ring-current' : 'bg-slate-900 border-slate-600 text-gray-400 hover:border-slate-400'}`}
                                    >
                                        <Icon className="w-4 h-4" /> {t}
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    <div>
                        <label className="text-xs text-gray-400 mb-1 block">Title (optional)</label>
                        <Input
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                            placeholder="e.g. Maintenance Notice"
                            className="bg-slate-900 border-blue-500/30 text-white"
                        />
                    </div>

                    <div>
                        <label className="text-xs text-gray-400 mb-1 block">Message *</label>
                        <textarea
                            value={message}
                            onChange={(e) => setMessage(e.target.value)}
                            placeholder="Type your message to all connected users..."
                            rows={4}
                            className="w-full px-3 py-2 rounded-md bg-slate-900 border border-blue-500/30 text-white placeholder:text-gray-500 focus:outline-none focus:ring-1 focus:ring-blue-500 resize-none text-sm"
                        />
                        <div className="flex justify-between mt-1">
                            <span className="text-xs text-gray-500">{message.length} characters</span>
                        </div>
                    </div>

                    <div>
                        <label className="text-xs text-gray-400 mb-1 block flex items-center gap-1">
                            <Clock className="w-3 h-3" /> Auto-expire after (minutes, optional)
                        </label>
                        <Input
                            type="number"
                            value={expiresIn}
                            onChange={(e) => setExpiresIn(e.target.value)}
                            placeholder="e.g. 60 (leave blank = never expires)"
                            className="bg-slate-900 border-blue-500/30 text-white max-w-xs"
                        />
                    </div>

                    <Button
                        onClick={send}
                        disabled={sending || !message.trim()}
                        className={`w-full ${sent ? 'bg-green-600 hover:bg-green-700' : 'bg-blue-600 hover:bg-blue-700'} text-white`}
                    >
                        <Send className="w-4 h-4 mr-2" />
                        {sending ? 'Sending...' : sent ? '✓ Sent to all users!' : `Broadcast to ${connectedCount} user${connectedCount !== 1 ? 's' : ''}`}
                    </Button>
                </CardContent>
            </Card>

            {/* Active Broadcasts */}
            {activeBroadcasts.length > 0 && (
                <Card className="bg-slate-800/50 border-blue-500/30">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-white text-base">Active Broadcasts ({activeBroadcasts.length})</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-3">
                        <AnimatePresence>
                            {activeBroadcasts.map(b => {
                                const Icon = TYPE_ICONS[b.type] || Info;
                                return (
                                    <motion.div
                                        key={b.id}
                                        initial={{ opacity: 0, y: -10 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        exit={{ opacity: 0, height: 0 }}
                                        className={`p-4 rounded-lg border ${TYPE_COLORS[b.type]}`}
                                    >
                                        <div className="flex items-start justify-between gap-3">
                                            <div className="flex items-start gap-3 flex-1 min-w-0">
                                                <Icon className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                                <div className="min-w-0">
                                                    {b.title && <p className="font-semibold text-sm">{b.title}</p>}
                                                    <p className="text-sm opacity-90">{b.message}</p>
                                                    <p className="text-xs opacity-60 mt-1">
                                                        Sent {new Date(b.created_date).toLocaleString()}
                                                        {b.expires_at && ` · Expires ${new Date(b.expires_at).toLocaleString()}`}
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="flex gap-1 flex-shrink-0">
                                                <Button variant="ghost" size="icon" onClick={() => deactivate(b.id)} className="w-7 h-7 text-gray-400 hover:text-yellow-300" title="Deactivate">
                                                    <EyeOff className="w-3.5 h-3.5" />
                                                </Button>
                                                <Button variant="ghost" size="icon" onClick={() => deleteBroadcast(b.id)} className="w-7 h-7 text-gray-400 hover:text-red-400" title="Delete">
                                                    <Trash2 className="w-3.5 h-3.5" />
                                                </Button>
                                            </div>
                                        </div>
                                    </motion.div>
                                );
                            })}
                        </AnimatePresence>
                    </CardContent>
                </Card>
            )}

            {/* History */}
            {broadcasts.filter(b => !b.is_active).length > 0 && (
                <Card className="bg-slate-800/50 border-slate-600/30">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-gray-400 text-base">Broadcast History</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-2">
                        {broadcasts.filter(b => !b.is_active).map(b => (
                            <div key={b.id} className="flex items-center justify-between p-3 rounded-lg bg-slate-900/50 border border-slate-700/30">
                                <div className="min-w-0 flex-1">
                                    {b.title && <span className="text-xs text-gray-400 font-medium mr-2">{b.title}:</span>}
                                    <span className="text-sm text-gray-500 truncate">{b.message}</span>
                                </div>
                                <div className="flex items-center gap-2 flex-shrink-0 ml-3">
                                    <span className="text-xs text-gray-600">{new Date(b.created_date).toLocaleDateString()}</span>
                                    <Button variant="ghost" size="icon" onClick={() => deleteBroadcast(b.id)} className="w-6 h-6 text-gray-600 hover:text-red-400">
                                        <Trash2 className="w-3 h-3" />
                                    </Button>
                                </div>
                            </div>
                        ))}
                    </CardContent>
                </Card>
            )}
        </div>
    );
}