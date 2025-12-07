import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { UserPlus, LogIn, Zap } from "lucide-react";
import { motion } from "framer-motion";
import { base44 } from "@/api/base44Client";

const LOGO_URL = "https://qtrypzzcjebvfcihiynt.supabase.co/storage/v1/object/public/base44-prod/public/68eeb940cdab39e5b8668523/ea04a0d3e_HushTVLogo.png";

export default function Welcome() {
  const navigate = useNavigate();
  const [showDemo, setShowDemo] = useState(true);
  const [isStartingDemo, setIsStartingDemo] = useState(false);

  useEffect(() => {
    const checkDemoAvailability = async () => {
      try {
        const { data } = await base44.functions.invoke('checkDemoTrial');
        setShowDemo(data.can_start_trial === true);
      } catch (error) {
        console.error('Error checking demo availability:', error);
      }
    };
    
    checkDemoAvailability();
  }, []);

  const handleDemoClick = async () => {
    setIsStartingDemo(true);
    
    try {
      const { data } = await base44.functions.invoke('startDemoTrial');
      
      if (data.success) {
        const demoAccount = {
          id: 'demo-account',
          name: 'Demo',
          username: 'Testline1',
          password: 'Testline1',
          host: 'http://nzlive.net',
          epgUrl: 'http://nzlive.net/xmltv.php?username=Testline1&password=Testline1'
        };

        localStorage.setItem('playlists', JSON.stringify([demoAccount]));
        sessionStorage.setItem('demoTrialRemaining', data.remaining_seconds);
        sessionStorage.setItem('demoMode', 'true');

        navigate(createPageUrl('Dashboard'), { replace: true });
      } else {
        alert(data.error || 'Unable to start demo');
        setIsStartingDemo(false);
      }
    } catch (error) {
      console.error('Error starting demo:', error);
      alert('Error starting demo. Please try again.');
      setIsStartingDemo(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-black">
      <div className="w-full max-w-4xl">
        <motion.div 
          initial={{ opacity: 0, y: -30 }} 
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="text-center mb-12"
        >
          <div className="w-full max-w-md mx-auto mb-6 bg-black rounded-xl p-8 shadow-2xl shadow-orange-500/20">
            <img src={LOGO_URL} alt="HushTV" className="w-full h-auto" />
          </div>
          <h2 className="text-2xl md:text-3xl font-bold text-white mb-2">Web Player</h2>
        </motion.div>

        <div className={`grid grid-cols-1 ${showDemo ? 'md:grid-cols-3' : 'md:grid-cols-2'} gap-6 md:gap-8`}
          <motion.div
            initial={{ opacity: 0, x: -50 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
          >
            <a href="https://hushtv.org/shop" target="_blank" rel="noopener noreferrer">
              <Card className="bg-gray-800/50 backdrop-blur-xl border-orange-500/30 hover:border-orange-500/60 hover:bg-gray-800/70 transition-all cursor-pointer group h-full">
                <CardContent className="p-8 md:p-12 flex flex-col items-center justify-center text-center h-full">
                  <div className="w-20 h-20 bg-gradient-to-br from-orange-500/20 to-orange-800/20 rounded-full flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    <UserPlus className="w-10 h-10 text-orange-400" />
                  </div>
                  <h3 className="text-2xl md:text-3xl font-bold text-white mb-2 group-hover:text-orange-300 transition-colors">
                    Sign Up To HushTV
                  </h3>
                  <p className="text-gray-400 text-sm md:text-base">Create your new account</p>
                </CardContent>
              </Card>
            </a>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, x: 50 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
          >
            <Link to={createPageUrl("AddAccount")}>
              <Card className="bg-gray-800/50 backdrop-blur-xl border-orange-500/30 hover:border-orange-500/60 hover:bg-gray-800/70 transition-all cursor-pointer group h-full">
                <CardContent className="p-8 md:p-12 flex flex-col items-center justify-center text-center h-full">
                  <div className="w-20 h-20 bg-gradient-to-br from-orange-500/20 to-orange-800/20 rounded-full flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    <LogIn className="w-10 h-10 text-orange-400" />
                  </div>
                  <h3 className="text-2xl md:text-3xl font-bold text-white mb-2 group-hover:text-orange-300 transition-colors">
                    Sign In To HushTV
                  </h3>
                  <p className="text-gray-400 text-sm md:text-base">Access your account</p>
                </CardContent>
              </Card>
            </Link>
          </motion.div>

          {showDemo && (
            <motion.div
              initial={{ opacity: 0, y: 50 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
            >
              <Card 
                onClick={handleDemoClick}
                className="bg-gradient-to-br from-orange-600/30 to-orange-800/30 backdrop-blur-xl border-orange-500/50 hover:border-orange-400 hover:from-orange-600/40 hover:to-orange-800/40 transition-all cursor-pointer group h-full"
              >
                <CardContent className="p-8 md:p-12 flex flex-col items-center justify-center text-center h-full">
                  <div className="w-20 h-20 bg-gradient-to-br from-orange-400/30 to-orange-600/30 rounded-full flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    <Zap className="w-10 h-10 text-orange-300" />
                  </div>
                  <h3 className="text-2xl md:text-3xl font-bold text-white mb-2 group-hover:text-orange-200 transition-colors">
                    {isStartingDemo ? 'Starting Demo...' : 'Try Demo'}
                  </h3>
                  <p className="text-orange-200 text-sm md:text-base">1 hour free trial</p>
                  {isStartingDemo && (
                    <div className="mt-4 animate-spin rounded-full h-6 w-6 border-b-2 border-orange-300"></div>
                  )}
                </CardContent>
              </Card>
            </motion.div>
          )}
        </div>
      </div>
    </div>
  );
}