import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Download, X, Share, Plus, Smartphone } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { motion, AnimatePresence } from 'framer-motion';
import { Card } from '@/components/ui/card';

export default function AddToHomeScreen({ showButton = false, autoPrompt = false }) {
  const [deferredPrompt, setDeferredPrompt] = useState(null);
  const [showInstallButton, setShowInstallButton] = useState(false);
  const [showIOSInstructions, setShowIOSInstructions] = useState(false);
  const [isInstalled, setIsInstalled] = useState(false);
  const [showPromptBanner, setShowPromptBanner] = useState(false);
  const [hasBeenDismissed, setHasBeenDismissed] = useState(false);

  const isIOS = () => {
    return /iPad|iPhone|iPod/.test(navigator.userAgent) || 
           (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  };

  const isInStandaloneMode = () => {
    return window.matchMedia('(display-mode: standalone)').matches || 
           window.navigator.standalone === true;
  };

  useEffect(() => {
    // Check if already installed
    if (isInStandaloneMode()) {
      setIsInstalled(true);
      return;
    }

    // Check if user has dismissed the prompt
    const dismissed = localStorage.getItem('installPromptDismissed') === 'true';
    setHasBeenDismissed(dismissed);

    // Show auto prompt banner if enabled and not dismissed
    if (autoPrompt && !dismissed) {
      setTimeout(() => {
        setShowPromptBanner(true);
      }, 2000); // Show after 2 seconds
    }

    // For iOS devices
    if (isIOS()) {
      setShowInstallButton(true);
    } else {
      // For Android/Desktop Chrome/Edge
      const handler = (e) => {
        e.preventDefault();
        setDeferredPrompt(e);
        setShowInstallButton(true);
      };

      window.addEventListener('beforeinstallprompt', handler);

      return () => {
        window.removeEventListener('beforeinstallprompt', handler);
      };
    }
  }, [autoPrompt]);

  const handleInstallClick = async () => {
    if (isIOS()) {
      setShowIOSInstructions(true);
      setShowPromptBanner(false);
    } else if (deferredPrompt) {
      deferredPrompt.prompt();
      const { outcome } = await deferredPrompt.userChoice;
      
      if (outcome === 'accepted') {
        setShowInstallButton(false);
        setIsInstalled(true);
        setShowPromptBanner(false);
        localStorage.setItem('installPromptDismissed', 'true');
      }
      
      setDeferredPrompt(null);
    }
  };

  const handleDismiss = () => {
    setShowPromptBanner(false);
    localStorage.setItem('installPromptDismissed', 'true');
    setHasBeenDismissed(true);
  };

  if (isInstalled) {
    return null;
  }

  if (!showInstallButton) {
    return null;
  }

  return (
    <>
      {/* Auto Prompt Banner */}
      <AnimatePresence>
        {showPromptBanner && !hasBeenDismissed && (
          <motion.div
            initial={{ opacity: 0, y: -100 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -100 }}
            transition={{ type: 'spring', damping: 25, stiffness: 300 }}
            className="fixed top-4 left-1/2 transform -translate-x-1/2 z-[100] w-[calc(100%-2rem)] max-w-md"
          >
            <Card className="bg-gradient-to-r from-orange-600 to-orange-800 border-orange-400/50 shadow-2xl">
              <div className="p-5">
                <div className="flex items-start gap-4">
                  <div className="w-12 h-12 bg-white/20 rounded-full flex items-center justify-center flex-shrink-0">
                    <Smartphone className="w-6 h-6 text-white" />
                  </div>
                  <div className="flex-1">
                    <h3 className="text-white font-bold text-lg mb-1">
                      Install HushTV WebPlayer
                    </h3>
                    <p className="text-orange-100 text-sm mb-4">
                      Get the full app experience! Add HushTV to your home screen for quick access.
                    </p>
                    <div className="flex gap-2">
                      <Button
                        onClick={handleInstallClick}
                        className="bg-white text-orange-700 hover:bg-orange-50 font-semibold shadow-lg"
                        size="sm"
                      >
                        <Download className="w-4 h-4 mr-2" />
                        Install Now
                      </Button>
                      <Button
                        onClick={handleDismiss}
                        variant="ghost"
                        className="text-white hover:bg-white/20"
                        size="sm"
                      >
                        Maybe Later
                      </Button>
                    </div>
                  </div>
                  <Button
                    onClick={handleDismiss}
                    variant="ghost"
                    size="icon"
                    className="text-white hover:bg-white/20 flex-shrink-0 h-8 w-8"
                  >
                    <X className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Regular Install Button (for Dashboard) */}
      {showButton && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="w-full"
        >
          <Button
            onClick={handleInstallClick}
            className="w-full bg-gradient-to-r from-orange-600 to-orange-800 hover:from-orange-700 hover:to-orange-900 text-white shadow-lg"
            size="lg"
          >
            <Download className="w-5 h-5 mr-2" />
            Add HushTV WebPlayer to Your Device
          </Button>
        </motion.div>
      )}

      {/* iOS Instructions Dialog */}
      <Dialog open={showIOSInstructions} onOpenChange={setShowIOSInstructions}>
        <DialogContent className="bg-gray-900 border-orange-500/30 text-white max-w-md">
          <DialogHeader>
            <DialogTitle className="text-2xl text-white flex items-center gap-2">
              <Download className="w-6 h-6 text-orange-400" />
              Install HushTV WebPlayer
            </DialogTitle>
            <DialogDescription className="text-gray-300">
              Follow these steps to add HushTV to your home screen
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-6 mt-4">
            <div className="flex items-start gap-4">
              <div className="w-10 h-10 bg-orange-600 rounded-full flex items-center justify-center flex-shrink-0 font-bold text-lg">
                1
              </div>
              <div>
                <p className="text-white font-semibold mb-1">Tap the Share button</p>
                <div className="flex items-center gap-2 text-gray-400 text-sm">
                  <Share className="w-5 h-5 text-blue-400" />
                  <span>Look for the share icon at the bottom of Safari</span>
                </div>
              </div>
            </div>

            <div className="flex items-start gap-4">
              <div className="w-10 h-10 bg-orange-600 rounded-full flex items-center justify-center flex-shrink-0 font-bold text-lg">
                2
              </div>
              <div>
                <p className="text-white font-semibold mb-1">Select "Add to Home Screen"</p>
                <div className="flex items-center gap-2 text-gray-400 text-sm">
                  <Plus className="w-5 h-5 text-gray-400" />
                  <span>Scroll down in the share menu to find this option</span>
                </div>
              </div>
            </div>

            <div className="flex items-start gap-4">
              <div className="w-10 h-10 bg-orange-600 rounded-full flex items-center justify-center flex-shrink-0 font-bold text-lg">
                3
              </div>
              <div>
                <p className="text-white font-semibold mb-1">Tap "Add"</p>
                <p className="text-gray-400 text-sm">HushTV will appear on your home screen like a native app!</p>
              </div>
            </div>

            <div className="bg-orange-900/20 border border-orange-500/30 rounded-lg p-4">
              <p className="text-orange-300 text-sm">
                💡 Once installed, you can launch HushTV directly from your home screen for a full-screen experience!
              </p>
            </div>

            <Button
              onClick={() => {
                setShowIOSInstructions(false);
                localStorage.setItem('installPromptDismissed', 'true');
                setHasBeenDismissed(true);
              }}
              className="w-full bg-orange-600 hover:bg-orange-700"
            >
              Got it!
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}