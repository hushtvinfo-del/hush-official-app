import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { adminAction } from '@/functions/adminAction';
import { Server, Tag, Trash2, Plus, Save } from 'lucide-react';

export default function SettingsTab({ configs, adminToken, onRefresh }) {
    const hostConfig = configs.find(c => c.key === 'host_override');
    const [hostOverride, setHostOverride] = useState(hostConfig?.value || '');
    const [savingHost, setSavingHost] = useState(false);

    // Category renames stored as JSON in one config key
    const categoryConfig = configs.find(c => c.key === 'category_renames');
    const initialRenames = (() => {
        try { return JSON.parse(categoryConfig?.value || '[]'); } catch { return []; }
    })();
    const [renames, setRenames] = useState(initialRenames);
    const [savingRenames, setSavingRenames] = useState(false);

    const saveHostOverride = async () => {
        setSavingHost(true);
        await adminAction({ admin_token: adminToken, action: 'set_config', payload: { key: 'host_override', value: hostOverride, label: 'IPTV Host Override' } });
        setSavingHost(false);
        onRefresh();
    };

    const saveRenames = async () => {
        setSavingRenames(true);
        await adminAction({ admin_token: adminToken, action: 'set_config', payload: { key: 'category_renames', value: JSON.stringify(renames), label: 'Category Name Overrides' } });
        setSavingRenames(false);
        onRefresh();
    };

    const addRename = () => setRenames([...renames, { original: '', display: '' }]);
    const removeRename = (i) => setRenames(renames.filter((_, idx) => idx !== i));
    const updateRename = (i, field, val) => {
        const copy = [...renames];
        copy[i] = { ...copy[i], [field]: val };
        setRenames(copy);
    };

    return (
        <div className="space-y-6">
            {/* Host Override */}
            <Card className="bg-slate-800/50 border-blue-500/30">
                <CardHeader className="pb-3">
                    <CardTitle className="text-white flex items-center gap-2">
                        <Server className="w-5 h-5 text-cyan-400" />
                        IPTV Host / DNS Override
                    </CardTitle>
                    <p className="text-sm text-gray-400">Override the Xtream server URL for all accounts. Leave blank to use each account's own host.</p>
                </CardHeader>
                <CardContent className="space-y-3">
                    <div className="flex gap-3">
                        <Input
                            value={hostOverride}
                            onChange={(e) => setHostOverride(e.target.value)}
                            placeholder="e.g. http://newserver.example.com:8080"
                            className="bg-slate-900 border-blue-500/30 text-white flex-1"
                        />
                        <Button onClick={saveHostOverride} disabled={savingHost} className="bg-blue-600 hover:bg-blue-700">
                            <Save className="w-4 h-4 mr-2" />
                            {savingHost ? 'Saving...' : 'Save'}
                        </Button>
                    </div>
                    {hostConfig && (
                        <p className="text-xs text-green-400">✓ Override active: {hostConfig.value}</p>
                    )}
                </CardContent>
            </Card>

            {/* Category Renames */}
            <Card className="bg-slate-800/50 border-blue-500/30">
                <CardHeader className="pb-3">
                    <CardTitle className="text-white flex items-center gap-2">
                        <Tag className="w-5 h-5 text-cyan-400" />
                        Category Name Overrides
                    </CardTitle>
                    <p className="text-sm text-gray-400">Rename category names as they appear to users. Original names must match exactly.</p>
                </CardHeader>
                <CardContent className="space-y-3">
                    <div className="space-y-2">
                        {renames.map((r, i) => (
                            <div key={i} className="flex gap-2 items-center">
                                <Input
                                    value={r.original}
                                    onChange={(e) => updateRename(i, 'original', e.target.value)}
                                    placeholder="Original name (exact)"
                                    className="bg-slate-900 border-blue-500/30 text-white flex-1"
                                />
                                <span className="text-gray-400 text-sm">→</span>
                                <Input
                                    value={r.display}
                                    onChange={(e) => updateRename(i, 'display', e.target.value)}
                                    placeholder="Display name"
                                    className="bg-slate-900 border-blue-500/30 text-white flex-1"
                                />
                                <Button variant="ghost" size="icon" onClick={() => removeRename(i)} className="text-red-400 hover:text-red-300 hover:bg-red-500/10">
                                    <Trash2 className="w-4 h-4" />
                                </Button>
                            </div>
                        ))}
                    </div>
                    <div className="flex gap-3 pt-2">
                        <Button variant="outline" onClick={addRename} className="border-blue-500/30 text-cyan-300 hover:bg-blue-500/10">
                            <Plus className="w-4 h-4 mr-2" /> Add Override
                        </Button>
                        <Button onClick={saveRenames} disabled={savingRenames} className="bg-blue-600 hover:bg-blue-700">
                            <Save className="w-4 h-4 mr-2" />
                            {savingRenames ? 'Saving...' : 'Save All'}
                        </Button>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}