import React, { useState, useEffect } from "react";
import { Clock } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export default function DemoTrialTimer() {
  const [timeRemaining, setTimeRemaining] = useState(null);
  const [show, setShow] = useState(false);

  useEffect(() => {
    const checkTimer = () => {
      try {
        const remaining = sessionStorage.getItem('demoTrialRemaining');
        if (remaining) {
          const seconds = parseInt(remaining);
          setTimeRemaining(seconds);
          setShow(seconds > 0 && seconds <= 3600);
        } else {
          setShow(false);
        }
      } catch (e) {
        setShow(false);
      }
    };

    checkTimer();
    const interval = setInterval(() => {
      if (timeRemaining !== null && timeRemaining > 0) {
        const newRemaining = timeRemaining - 1;
        setTimeRemaining(newRemaining);
        sessionStorage.setItem('demoTrialRemaining', newRemaining.toString());
        
        if (newRemaining <= 0) {
          window.location.reload();
        }
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [timeRemaining]);

  const formatTime = (seconds) => {
    if (!seconds || seconds <= 0) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const isLowTime = timeRemaining && timeRemaining <= 300; // 5 minutes

  return (
    <AnimatePresence>
      {show && timeRemaining !== null && (
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          className={`fixed top-4 right-4 z-50 ${
            isLowTime ? 'bg-red-500/90' : 'bg-orange-500/90'
          } backdrop-blur-sm text-white px-4 py-2 rounded-lg shadow-lg flex items-center gap-2`}
        >
          <Clock className="w-4 h-4" />
          <div className="text-sm font-semibold">
            Demo Trial: {formatTime(timeRemaining)}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}