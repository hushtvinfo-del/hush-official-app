import React, { useState } from "react";
import { base44 } from "@/api/base44Client";
import { useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ArrowLeft, AlertCircle, Loader2, UserPlus } from "lucide-react";
import { motion } from "framer-motion";

export default function AddAccount() {
  const navigate = useNavigate();
  const [accountName, setAccountName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  // Hardcoded host
  const HUSH_HOST = "https://hushvipnew.ink:443";

  // Check if user has any existing accounts
  const hasExistingAccounts = () => {
    try {
      const playlists = JSON.parse(localStorage.getItem('playlists') || '[]');
      return playlists.length > 0;
    } catch {
      return false;
    }
  };

  const handleBack = () => {
    if (hasExistingAccounts()) {
      navigate(createPageUrl("Dashboard"));
    } else {
      navigate(createPageUrl("Welcome"));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!accountName.trim() || !username.trim() || !password.trim()) {
      setError("Please fill in all fields");
      return;
    }
    setIsLoading(true);

    try {
      // Verify credentials using hardcoded host
      const { data: responseData } = await base44.functions.invoke('xtreamProxy', {
          host: HUSH_HOST,
          username: username,
          password: password,
          params: {} // Empty params just to authenticate
      });

      if (responseData?.user_info?.auth === 0 || !responseData?.server_info) {
          throw new Error("Authentication failed. Please check your username and password.");
      }
      
      // Construct EPG URL from server info
      const { url, port, https_port } = responseData.server_info;
      const protocol = https_port ? 'https' : 'http';
      const serverPort = https_port || port;
      const epgUrl = `${protocol}://${url}:${serverPort}/xmltv.php?username=${username}&password=${password}`;

      // Save to local storage with hardcoded host
      const newPlaylist = {
        id: crypto.randomUUID(),
        name: accountName,
        username,
        password,
        host: HUSH_HOST, // Use hardcoded host
        epg_url: epgUrl,
        is_active: true
      };
      
      const existingPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
      const updatedPlaylists = [...existingPlaylists, newPlaylist];
      localStorage.setItem('playlists', JSON.stringify(updatedPlaylists));
      
      setIsLoading(false);
      navigate(createPageUrl("Dashboard"));

    } catch (error) {
      setError(error.message || "Failed to create account. Please try again.");
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen p-4 md:p-8 bg-gradient-to-br from-slate-950 via-blue-950 to-slate-900 flex items-center justify-center">
      <div className="max-w-xl w-full">
        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
          <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30">
            <CardHeader>
              <CardTitle className="text-2xl text-white flex items-center gap-2">
                <UserPlus className="w-6 h-6 text-cyan-400" />
                Sign In to HushTV Web Player
              </CardTitle>
              <CardDescription className="text-cyan-300">Enter your HushTV credentials to get started.</CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-6">
                <div className="space-y-2">
                  <Label htmlFor="username" className="text-white">Username</Label>
                  <Input id="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Your HushTV username" className="bg-slate-900/50 border-blue-500/30 text-white" disabled={isLoading} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="password" className="text-white">Password</Label>
                  <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Your HushTV password" className="bg-slate-900/50 border-blue-500/30 text-white" disabled={isLoading} />
                </div>
                 <div className="space-y-2">
                  <Label htmlFor="name" className="text-white">Account Nickname</Label>
                  <Input id="name" value={accountName} onChange={(e) => setAccountName(e.target.value)} placeholder="e.g., Home TV" className="bg-slate-900/50 border-blue-500/30 text-white" disabled={isLoading} />
                </div>
                {error && <Alert variant="destructive" className="bg-red-900/20 border-red-500/30"><AlertCircle className="h-4 w-4" /><AlertDescription className="text-red-400">{error}</AlertDescription></Alert>}
                <Button type="submit" disabled={isLoading} className="w-full bg-gradient-to-r from-blue-600 to-blue-800 hover:from-blue-700 hover:to-blue-900 text-white shadow-lg shadow-blue-500/50">
                  {isLoading ? <><Loader2 className="w-5 h-5 mr-2 animate-spin" />Verifying & Saving...</> : 'Sign In'}
                </Button>
              </form>
            </CardContent>
          </Card>
        </motion.div>
        
        <div className="mt-6">
          <Button variant="ghost" onClick={handleBack} className="w-full text-cyan-300 hover:text-white hover:bg-blue-500/20">
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back
          </Button>
        </div>
      </div>
    </div>
  );
}