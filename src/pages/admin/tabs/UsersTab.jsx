import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { adminAction } from '@/functions/adminAction';
import { Users, Monitor, Tv, Smartphone, Tablet, Search, Trash2, Wifi, WifiOff, Film, Radio, BookOpen } from 'lucide-react';

const DEVICE_ICONS = { desktop: Monitor, tv: Tv, mobile: Smartphone, tablet: Tablet, unknown: Monitor };
const CONTENT_COLORS = {
    live: 'bg-red-500/20 text-red-300 border-red-500/40',
    movie: 'bg-blue-500/20 text-blue-300 border-blue-500/40',
    series: 'bg-purple-500/20 text-purple-300 border-purple-500/40',
    idle: 'bg-slate-600/20 text-slate-400 border-slate-600/40',
};
const CONTENT_ICONS = { live: Radio, movie: Film, series: BookOpen, idle: WifiOff };

function timeSince(dateStr) {
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
}

function isActive(lastSeen) {
    return Date.now() - new Date(lastSeen).getTime() < 10 * 60 * 1000;
}

export default function UsersTab({ sessions, adminToken, onRefresh }) {
    const [search, setSearch] = useState('');
    const [filter, setFilter] = useState('all');
    const [clearing, setClearing] = useState(false);

    const filtered = sessions.filter(s => {
        const matchSearch = !search || 
            s.account_name?.toLowerCase().includes(search.toLowerCase()) ||
            s.username?.toLowerCase().includes(search.toLowerCase()) ||
            s.ip_address?.includes(search) ||
            s.current_content?.toLowerCase().includes(search.toLowerCase());
        
        if (filter === 'active') return matchSearch && isActive(s.last_seen);
        if (filter === 'live') return matchSearch && s.content_type === 'live';
        if (filter === 'movie') return matchSearch && s.content_type === 'movie';
        if (filter === 'series') return matchSearch && s.content_type === 'series';
        return matchSearch;
    });

    const activeCount = sessions.filter(s => isActive(s.last_seen)).length;

    const deleteSession = async (id) => {
        await adminAction({ admin_token: adminToken, action: 'delete_session', payload: { id } });
        onRefresh();
    };

    const clearOld = async () => {
        setClearing(true);
        await adminAction({ admin_token: adminToken, action: 'clear_old_sessions', payload: {} });
        setClearing(false);
        onRefresh();
    };

    return (
        <div className="space-y-4">
            {/* Stats bar */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {[
                    { label: 'Active Now', value: activeCount, color: 'text-green-400', icon: Wifi },
                    { label: 'Watching Live', value: sessions.filter(s => isActive(s.last_seen) && s.content_type === 'live').length, color: 'text-red-400', icon: Radio },
                    { label: 'Watching Movies', value: sessions.filter(s => isActive(s.last_seen) && s.content_type === 'movie').length, color: 'text-blue-400', icon: Film },
                    { label: 'Watching Series', value: sessions.filter(s => isActive(s.last_seen) && s.content_type === 'series').length, color: 'text-purple-400', icon: BookOpen },
                ].map(stat => {
                    const Icon = stat.icon;
                    return (
                        <Card key={stat.label} className="bg-slate-800/50 border-blue-500/20">
                            <CardContent className="p-4 flex items-center gap-3">
                                <Icon className={`w-6 h-6 ${stat.color}`} />
                                <div>
                                    <p className={`text-2xl font-bold ${stat.color}`}>{stat.value}</p>
                                    <p className="text-xs text-gray-400">{stat.label}</p>
                                </div>
                            </CardContent>
                        </Card>
                    );
                })}
            </div>

            {/* Filters + Search */}
            <div className="flex flex-col sm:flex-row gap-3">
                <div className="relative flex-1">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                    <Input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by name, IP, content..." className="pl-9 bg-slate-800 border-blue-500/30 text-white" />
                </div>
                <div className="flex gap-2 flex-wrap">
                    {['all', 'active', 'live', 'movie', 'series'].map(f => (
                        <button key={f} onClick={() => setFilter(f)} className={`px-3 py-1.5 rounded-lg text-xs font-medium capitalize border transition-all ${filter === f ? 'bg-blue-600 border-blue-500 text-white' : 'bg-slate-800 border-slate-600 text-gray-400 hover:border-slate-400'}`}>
                            {f}
                        </button>
                    ))}
                </div>
                <Button variant="outline" size="sm" onClick={clearOld} disabled={clearing} className="border-red-500/30 text-red-400 hover:bg-red-500/10 whitespace-nowrap">
                    <Trash2 className="w-3.5 h-3.5 mr-1.5" />
                    {clearing ? 'Clearing...' : 'Clear Old'}
                </Button>
            </div>

            {/* Sessions list */}
            <div className="space-y-2">
                {filtered.length === 0 ? (
                    <div className="text-center py-12 text-gray-500">
                        <Users className="w-12 h-12 mx-auto mb-3 opacity-30" />
                        <p>No sessions found</p>
                    </div>
                ) : (
                    filtered.map(session => {
                        const DeviceIcon = DEVICE_ICONS[session.device_type] || Monitor;
                        const ContentIcon = CONTENT_ICONS[session.content_type] || WifiOff;
                        const active = isActive(session.last_seen);

                        return (
                            <Card key={session.id} className={`border transition-all ${active ? 'bg-slate-800/60 border-blue-500/30' : 'bg-slate-900/40 border-slate-700/30'}`}>
                                <CardContent className="p-4">
                                    <div className="flex items-start justify-between gap-3">
                                        <div className="flex items-start gap-3 min-w-0 flex-1">
                                            <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${active ? 'bg-green-500/20' : 'bg-slate-700/50'}`}>
                                                <DeviceIcon className={`w-4 h-4 ${active ? 'text-green-400' : 'text-gray-500'}`} />
                                            </div>
                                            <div className="min-w-0 flex-1">
                                                <div className="flex items-center gap-2 flex-wrap mb-1">
                                                    <span className="font-semibold text-white text-sm">{session.account_name || 'Unknown'}</span>
                                                    {session.username && <span className="text-xs text-gray-400">@{session.username}</span>}
                                                    <span className={`w-2 h-2 rounded-full flex-shrink-0 ${active ? 'bg-green-400' : 'bg-gray-600'}`} />
                                                    <span className="text-xs text-gray-500">{timeSince(session.last_seen)}</span>
                                                </div>

                                                {/* Content being watched */}
                                                {session.current_content && (
                                                    <div className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md border text-xs mb-2 ${CONTENT_COLORS[session.content_type]}`}>
                                                        <ContentIcon className="w-3 h-3" />
                                                        <span className="truncate max-w-xs">{session.current_content}</span>
                                                    </div>
                                                )}

                                                {/* Details grid */}
                                                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-0.5 mt-1">
                                                    <InfoRow label="IP" value={session.ip_address} />
                                                    <InfoRow label="Device" value={session.device_type} />
                                                    <InfoRow label="Host" value={session.playlist_host} truncate />
                                                    {session.account_expiry && <InfoRow label="Expires" value={session.account_expiry} highlight={isExpiringSoon(session.account_expiry)} />}
                                                    {session.max_connections && <InfoRow label="Max Conn." value={session.max_connections} />}
                                                    {session.active_connections && <InfoRow label="Active Conn." value={session.active_connections} />}
                                                </div>

                                                {/* User agent */}
                                                <p className="text-xs text-gray-600 mt-1 truncate" title={session.user_agent}>{session.user_agent}</p>
                                            </div>
                                        </div>
                                        <Button variant="ghost" size="icon" onClick={() => deleteSession(session.id)} className="w-7 h-7 text-gray-600 hover:text-red-400 flex-shrink-0">
                                            <Trash2 className="w-3.5 h-3.5" />
                                        </Button>
                                    </div>
                                </CardContent>
                            </Card>
                        );
                    })
                )}
            </div>
        </div>
    );
}

function InfoRow({ label, value, truncate, highlight }) {
    if (!value) return null;
    return (
        <div className="flex items-center gap-1">
            <span className="text-xs text-gray-500 flex-shrink-0">{label}:</span>
            <span className={`text-xs font-mono ${highlight ? 'text-red-400' : 'text-gray-300'} ${truncate ? 'truncate' : ''}`}>{value}</span>
        </div>
    );
}

function isExpiringSoon(expiryStr) {
    if (!expiryStr) return false;
    const expiry = new Date(expiryStr);
    return expiry < new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
}