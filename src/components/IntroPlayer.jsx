import React, { useRef, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { SkipForward } from "lucide-react";

export default function IntroPlayer({ videoUrl, onFinished }) {
  const videoRef = useRef(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    // Try to play, but if it fails (autoplay blocked or video not found), skip intro
    const playPromise = video.play();
    
    if (playPromise !== undefined) {
      playPromise.catch(error => {
        console.log('Autoplay blocked or video unavailable, skipping intro:', error.message);
        // Immediately skip to main app if autoplay fails
        onFinished();
      });
    }

    // Set a backup timeout - if video doesn't load in 3 seconds, skip
    const loadTimeout = setTimeout(() => {
      if (video.readyState < 2) { // HAVE_CURRENT_DATA
        console.log('Video loading timeout, skipping intro');
        onFinished();
      }
    }, 3000);

    return () => clearTimeout(loadTimeout);
  }, [onFinished]);

  return (
    <div className="fixed inset-0 bg-black flex items-center justify-center z-50">
      <video
        ref={videoRef}
        className="w-full h-full object-cover"
        muted
        playsInline
        onEnded={onFinished}
        onError={(e) => {
          console.log('Video load error, skipping intro:', e.target.error?.message);
          onFinished();
        }}
      >
        <source src={videoUrl} type="video/mp4" />
      </video>
      
      {/* Skip button */}
      <Button
        onClick={onFinished}
        className="absolute bottom-8 right-8 bg-orange-600/90 hover:bg-orange-700 backdrop-blur-sm"
      >
        <SkipForward className="w-4 h-4 mr-2" />
        Skip Intro
      </Button>
    </div>
  );
}