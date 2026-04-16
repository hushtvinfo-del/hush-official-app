import React, { useState, useEffect, useCallback } from 'react';
import AdminLogin from './admin/AdminLogin';
import SettingsTab from './admin/tabs/SettingsTab';
import BroadcastTab from './admin/tabs/BroadcastTab';
import UsersTab from './admin/tabs/UsersTab';
import AnalyticsTab from './admin/tabs/AnalyticsTab';
import { getAdminData } from '@/functions/getAdminData';
import { Button } from '@/components/ui/button';
import { ShieldCheck, Settings, Megaphone, Users, BarChart2, LogOut, RefreshCw, Wifi } from 'lucide-react';

const TABS = [
    { id: 'users', label: 'Connected Users', icon: Users },
    { id: 'broadcast', label: 'Broadcast', icon: Megaphone },
    { id: 'settings', label: 'Settings', icon: Settings },
    { id: 'analytics', label: 'Analytics', icon: BarChart2 },
];

export default function Admin() {
    const [adminToken, setAdminToken] = useState(() => sessionStorage.getItem('admin_auth') || null);
    const [activeTab, setActiveTab] = useState('users');
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [lastRefresh, setLastRefresh] = useState(null);

    const fetchData = useCallback(async (token) => {
        if (!token) return;
        setLoading(true);
        try {
            const res = await getAdminData({ admin_token: token });
            setData(res.data);
            setLastRefresh(new Date());
        } catch (e) {
            console.error('Admin data fetch failed:', e);
        }
        setLoading(false);
    }, []);

    useEffect(() => {
        if (adminToken) {
            fetchData(adminToken);
            const interval = setInterval(() => fetchData(adminToken), 30000);
            return () => clearInterval(interval);
        }
    }, [adminToken, fetchData]);

    const handleLogin = (token) => {
        setAdminToken(token);
        fetchData(token);
    };

    const handleLogout = () => {
        sessionStorage.removeItem('admin_auth');
        setAdminToken(null);
        setData(null);
    };

    if (!adminToken) {
        return <AdminLogin onLogin={handleLogin} />;
    }

    return (
        <div className="min-h-screen bg-black text-white">
            {/* Header */}
            <div className="bg-slate-900 border-b border-blue-500/20 px-4 md:px-8 py-4 sticky top-0 z-40">
                <div className="max-w-7xl mx-auto flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <ShieldCheck className="w-6 h-6 text-blue-400" />
                        <div>
                            <h1 className="text-lg font-bold text-white leading-none">HushTV Admin</h1>
                            <p className="text-xs text-gray-500">Control Center</p>
                        </div>
                        {data && (
                            <div className="flex items-center gap-1.5 ml-4 px-3 py-1 rounded-full bg-green-500/10 border border-green-500/30">
                                <Wifi className="w-3 h-3 text-green-400" />
                                <span className="text-xs text-green-300 font-medium">{data.stats?.total_connected || 0} online</span>
                            </div>
                        )}
                    </div>
                    <div className="flex items-center gap-2">
                        {lastRefresh && (
                            <span className="text-xs text-gray-600 hidden sm:block">
                                Updated {lastRefresh.toLocaleTimeString()}
                            </span>
                        )}
                        <Button variant="ghost" size="icon" onClick={() => fetchData(adminToken)} disabled={loading} className="text-gray-400 hover:text-white w-8 h-8">
                            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                        </Button>
                        <Button variant="ghost" size="sm" onClick={handleLogout} className="text-gray-400 hover:text-red-400">
                            <LogOut className="w-4 h-4 mr-1.5" /> Logout
                        </Button>
                    </div>
                </div>
            </div>

            {/* Tab Nav */}
            <div className="bg-slate-900/50 border-b border-blue-500/10 px-4 md:px-8">
                <div className="max-w-7xl mx-auto flex gap-1 overflow-x-auto">
                    {TABS.map(tab => {
                        const Icon = tab.icon;
                        return (
                            <button
                                key={tab.id}
                                onClick={() => setActiveTab(tab.id)}
                                className={`flex items-center gap-2 px-4 py-3 text-sm font-medium whitespace-nowrap border-b-2 transition-all ${
                                    activeTab === tab.id
                                        ? 'border-blue-500 text-blue-300'
                                        : 'border-transparent text-gray-400 hover:text-white hover:border-slate-600'
                                }`}
                            >
                                <Icon className="w-4 h-4" />
                                {tab.label}
                                {tab.id === 'broadcast' && data?.broadcasts?.filter(b => b.is_active).length > 0 && (
                                    <span className="bg-blue-500 text-white text-xs rounded-full w-4 h-4 flex items-center justify-center">
                                        {data.broadcasts.filter(b => b.is_active).length}
                                    </span>
                                )}
                                {tab.id === 'users' && data?.stats?.total_connected > 0 && (
                                    <span className="bg-green-500 text-white text-xs rounded-full px-1.5 h-4 flex items-center justify-center">
                                        {data.stats.total_connected}
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>
            </div>

            {/* Content */}
            <div className="max-w-7xl mx-auto px-4 md:px-8 py-6">
                {!data && loading ? (
                    <div className="flex items-center justify-center py-24">
                        <RefreshCw className="w-8 h-8 text-blue-400 animate-spin" />
                    </div>
                ) : data ? (
                    <>
                        {activeTab === 'users' && (
                            <UsersTab sessions={data.allSessions} adminToken={adminToken} onRefresh={() => fetchData(adminToken)} />
                        )}
                        {activeTab === 'broadcast' && (
                            <BroadcastTab
                                broadcasts={data.broadcasts}
                                adminToken={adminToken}
                                connectedCount={data.stats?.total_connected || 0}
                                onRefresh={() => fetchData(adminToken)}
                            />
                        )}
                        {activeTab === 'settings' && (
                            <SettingsTab configs={data.configs} adminToken={adminToken} onRefresh={() => fetchData(adminToken)} />
                        )}
                        {activeTab === 'analytics' && (
                            <AnalyticsTab watchProgress={data.watchProgress} />
                        )}
                    </>
                ) : (
                    <div className="text-center py-24 text-gray-500">Failed to load data. <button onClick={() => fetchData(adminToken)} className="text-blue-400 hover:underline">Retry</button></div>
                )}
            </div>
        </div>
    );
}