import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { BarChart2, Film, BookOpen, Search, TrendingUp, Clock } from 'lucide-react';

function timeAgo(dateStr) {
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
}

function formatDuration(secs) {
    if (!secs) return '--';
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
}

export default function AnalyticsTab({ watchProgress }) {
    const [search, setSearch] = useState('');

    const filtered = watchProgress.filter(w =>
        !search || w.content_info?.name?.toLowerCase().includes(search.toLowerCase()) || w.user_email?.toLowerCase().includes(search.toLowerCase())
    );

    // Top content by watch count
    const contentCounts = {};
    watchProgress.forEach(w => {
        const name = w.content_info?.name || 'Unknown';
        contentCounts[name] = (contentCounts[name] || 0) + 1;
    });
    const topContent = Object.entries(contentCounts).sort((a, b) => b[1] - a[1]).slice(0, 10);

    // Stats
    const movies = watchProgress.filter(w => w.content_type === 'movie');
    const episodes = watchProgress.filter(w => w.content_type === 'episode');
    const uniqueUsers = new Set(watchProgress.map(w => w.user_email)).size;

    return (
        <div className="space-y-6">
            {/* Stats */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {[
                    { label: 'Total Views', value: watchProgress.length, icon: TrendingUp, color: 'text-cyan-400' },
                    { label: 'Movies Watched', value: movies.length, icon: Film, color: 'text-blue-400' },
                    { label: 'Episodes Watched', value: episodes.length, icon: BookOpen, color: 'text-purple-400' },
                    { label: 'Unique Accounts', value: uniqueUsers, icon: BarChart2, color: 'text-green-400' },
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

            {/* Top Content */}
            <Card className="bg-slate-800/50 border-blue-500/30">
                <CardHeader className="pb-3">
                    <CardTitle className="text-white text-base flex items-center gap-2">
                        <TrendingUp className="w-4 h-4 text-cyan-400" /> Top Watched Content
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="space-y-2">
                        {topContent.map(([name, count], i) => (
                            <div key={name} className="flex items-center gap-3">
                                <span className="text-xs text-gray-500 w-5 text-right">{i + 1}</span>
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center justify-between mb-0.5">
                                        <span className="text-sm text-white truncate">{name}</span>
                                        <span className="text-xs text-cyan-300 ml-2 flex-shrink-0">{count} view{count !== 1 ? 's' : ''}</span>
                                    </div>
                                    <div className="h-1.5 bg-slate-700 rounded-full overflow-hidden">
                                        <div className="h-full bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full" style={{ width: `${Math.round((count / topContent[0][1]) * 100)}%` }} />
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </CardContent>
            </Card>

            {/* Watch History Table */}
            <Card className="bg-slate-800/50 border-blue-500/30">
                <CardHeader className="pb-3">
                    <div className="flex items-center justify-between">
                        <CardTitle className="text-white text-base flex items-center gap-2">
                            <Clock className="w-4 h-4 text-cyan-400" /> Watch History ({filtered.length})
                        </CardTitle>
                        <div className="relative w-56">
                            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400" />
                            <Input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search..." className="pl-8 h-8 text-xs bg-slate-900 border-blue-500/30 text-white" />
                        </div>
                    </div>
                </CardHeader>
                <CardContent className="p-0">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-slate-700/50">
                                    <th className="text-left text-xs text-gray-400 font-medium px-4 py-2">Content</th>
                                    <th className="text-left text-xs text-gray-400 font-medium px-4 py-2">Type</th>
                                    <th className="text-left text-xs text-gray-400 font-medium px-4 py-2">Account</th>
                                    <th className="text-left text-xs text-gray-400 font-medium px-4 py-2">Progress</th>
                                    <th className="text-left text-xs text-gray-400 font-medium px-4 py-2">Duration</th>
                                    <th className="text-left text-xs text-gray-400 font-medium px-4 py-2">Last Watched</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.slice(0, 100).map(w => {
                                    const pct = w.duration > 0 ? Math.round((w.progress / w.duration) * 100) : 0;
                                    return (
                                        <tr key={w.id} className="border-b border-slate-800/50 hover:bg-slate-800/30">
                                            <td className="px-4 py-2 max-w-xs">
                                                <div className="flex items-center gap-2">
                                                    {w.content_info?.cover_image && (
                                                        <img src={w.content_info.cover_image} alt="" className="w-6 h-8 object-cover rounded flex-shrink-0" onError={e => e.target.style.display = 'none'} />
                                                    )}
                                                    <span className="text-white text-xs truncate">{w.content_info?.name || 'Unknown'}</span>
                                                </div>
                                            </td>
                                            <td className="px-4 py-2">
                                                <span className={`text-xs px-1.5 py-0.5 rounded ${w.content_type === 'movie' ? 'bg-blue-500/20 text-blue-300' : 'bg-purple-500/20 text-purple-300'}`}>
                                                    {w.content_type}
                                                </span>
                                            </td>
                                            <td className="px-4 py-2 text-xs text-gray-400 font-mono">{w.user_email || '--'}</td>
                                            <td className="px-4 py-2">
                                                <div className="flex items-center gap-2">
                                                    <div className="w-16 h-1.5 bg-slate-700 rounded-full overflow-hidden">
                                                        <div className="h-full bg-blue-500 rounded-full" style={{ width: `${pct}%` }} />
                                                    </div>
                                                    <span className="text-xs text-gray-400">{pct}%</span>
                                                </div>
                                            </td>
                                            <td className="px-4 py-2 text-xs text-gray-400">{formatDuration(w.duration)}</td>
                                            <td className="px-4 py-2 text-xs text-gray-500">{timeAgo(w.updated_date)}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}