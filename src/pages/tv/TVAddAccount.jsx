import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { base44 } from '@/api/base44Client';
import { HushTVLogo } from './TVApp';
import { Loader2, AlertCircle, UserPlus } from 'lucide-react';

const HUSH_HOST = "https://hushvipnew.ink:443";

export default function TVAddAccount() {
  const navigate = useNavigate();
  const [accountName, setAccountName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [focused, setFocused] = useState(0);
  const refs = useRef([]);

  const fields = [
    { label: 'Username', value: username, setter: setUsername, type: 'text', placeholder: 'Your HushTV username' },
    { label: 'Password', value: password, setter: setPassword, type: 'password', placeholder: 'Your HushTV password' },
    { label: 'Account Nickname', value: accountName, setter: setAccountName, type: 'text', placeholder: 'e.g., Living Room TV' },
  ];

  useEffect(() => {
    if (refs.current[focused]) refs.current[focused].focus();
  }, [focused]);

  const handleSubmit = async () => {
    setError(null);
    if (!accountName.trim() || !username.trim() || !password.trim()) {
      setError('Please fill in all fields');
      return;
    }
    setIsLoading(true);
    try {
      const { data: responseData } = await base44.functions.invoke('xtreamProxy', {
        host: HUSH_HOST, username, password, params: {}
      });
      if (responseData?.user_info?.auth === 0 || !responseData?.server_info) {
        throw new Error('Invalid username or password. Please try again.');
      }
      const { url, port, https_port } = responseData.server_info;
      const protocol = https_port ? 'https' : 'http';
      const serverPort = https_port || port;
      const epgUrl = `${protocol}://${url}:${serverPort}/xmltv.php?username=${username}&password=${password}`;
      const newPlaylist = {
        id: crypto.randomUUID(),
        name: accountName,
        username,
        password,
        host: HUSH_HOST,
        epg_url: epgUrl,
        is_active: true
      };
      const existing = JSON.parse(localStorage.getItem('playlists') || '[]');
      localStorage.setItem('playlists', JSON.stringify([...existing, newPlaylist]));
      navigate('/tv');
    } catch (err) {
      setError(err.message || 'Failed to sign in. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e, index) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      const next = Math.min(index + 1, fields.length); // fields.length = submit button
      setFocused(next);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setFocused(Math.max(index - 1, 0));
    } else if (e.key === 'Enter' && index === fields.length) {
      handleSubmit();
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-16"
      style={{ background: 'radial-gradient(ellipse at 50% 20%, #0f2657 0%, #000 70%)' }}>
      <div className="w-full max-w-2xl">
        <div className="flex items-center gap-4 mb-12">
          <HushTVLogo size="lg" />
        </div>

        <h1 className="text-4xl font-bold text-white mb-2 flex items-center gap-3">
          <UserPlus className="w-9 h-9 text-cyan-400" />
          Sign In to HushTV
        </h1>
        <p className="text-gray-400 text-xl mb-10">Enter your credentials to add an account</p>

        <div className="space-y-6">
          {fields.map((field, i) => (
            <div key={field.label}>
              <label className="block text-gray-300 text-lg font-semibold mb-2">{field.label}</label>
              <input
                ref={el => refs.current[i] = el}
                type={field.type}
                value={field.value}
                onChange={e => field.setter(e.target.value)}
                placeholder={field.placeholder}
                onFocus={() => setFocused(i)}
                onKeyDown={e => handleKeyDown(e, i)}
                className="w-full px-6 py-5 rounded-xl text-white text-xl outline-none tv-focus"
                style={{
                  background: 'rgba(255,255,255,0.08)',
                  border: focused === i ? '2px solid #06b6d4' : '2px solid rgba(255,255,255,0.15)',
                  fontSize: '1.25rem'
                }}
              />
            </div>
          ))}

          {error && (
            <div className="flex items-center gap-3 px-5 py-4 rounded-xl"
              style={{ background: 'rgba(239,68,68,0.15)', border: '1px solid rgba(239,68,68,0.4)' }}>
              <AlertCircle className="w-6 h-6 text-red-400 flex-shrink-0" />
              <span className="text-red-300 text-lg">{error}</span>
            </div>
          )}

          <button
            ref={el => refs.current[fields.length] = el}
            onClick={handleSubmit}
            onFocus={() => setFocused(fields.length)}
            onKeyDown={e => handleKeyDown(e, fields.length)}
            disabled={isLoading}
            className="tv-card tv-focus w-full py-6 rounded-xl text-white text-xl font-bold flex items-center justify-center gap-3 mt-2"
            style={{ background: 'linear-gradient(135deg, #3b82f6, #06b6d4)' }}
          >
            {isLoading ? <><Loader2 className="w-6 h-6 animate-spin" /> Verifying...</> : 'Sign In'}
          </button>

          <button
            onClick={() => navigate('/tv')}
            className="tv-focus w-full py-4 rounded-xl text-gray-400 text-lg font-semibold"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)' }}
          >
            ← Back
          </button>
        </div>
      </div>
    </div>
  );
}