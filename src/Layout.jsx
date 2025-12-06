import React, { useState, useEffect } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Tv2, Plus, Grid3x3, User, LogOut } from "lucide-react";
import { base44 } from "@/api/base44Client";
import {
  Sidebar,
  SidebarContent,
  SidebarHeader,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import IntroPlayer from "@/components/IntroPlayer";
import { detectDevice, getPerformanceConfig } from "@/components/deviceDetection";

const INTRO_VIDEO_URL = "https://assets.mixkit.co/videos/preview/mixkit-abstract-circular-light-trails-34208-large.mp4";

const LOGO_URL = "https://qtrypzzcjebvfcihiynt.supabase.co/storage/v1/object/public/base44-prod/public/68eeb940cdab39e5b8668523/ea04a0d3e_HushTVLogo.png";

export default function Layout({ children, currentPageName }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [user, setUser] = React.useState(null);
  const [showIntro, setShowIntro] = useState(false);
  const [deviceInfo, setDeviceInfo] = useState(null);
  const [perfConfig, setPerfConfig] = useState(null);

  useEffect(() => {
    // Detect device and set performance config
    const device = detectDevice();
    const config = getPerformanceConfig(device.deviceType);
    setDeviceInfo(device);
    setPerfConfig(config);
    
    console.log('🖥️ Device detected:', device.deviceType, device.isTV ? '(TV Mode Enabled)' : '');
    
    // Public app - no Base44 authentication needed
    setUser(null);
    
    const introHasPlayed = sessionStorage.getItem('introPlayed') === 'true';
    if (!introHasPlayed && !device.isTV) {
        setShowIntro(true);
    }

    // Check if user should be redirected to Welcome page
    // Only redirect on initial load and if not already on Welcome/SignUp pages
    const pagesWithoutRedirect = ['Welcome', 'SignUp', 'AddAccount'];
    if (!pagesWithoutRedirect.includes(currentPageName)) {
      try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        if (localPlaylists.length === 0 && currentPageName !== 'Welcome') {
          navigate(createPageUrl('Welcome'), { replace: true });
        }
      } catch (e) {
        console.error('Error checking playlists:', e);
      }
    }

    const manifest = {
      short_name: "HushTV",
      name: "HushTV Web Player",
      icons: [
        {
          src: "https://qtrypzzcjebvfcihiynt.supabase.co/storage/v1/object/public/base44-prod/apps/68eeb940-cdab-39e5-b866-85235b0baac9/res/tv-icon-192.png",
          type: "image/png",
          sizes: "192x192"
        },
        {
          src: "https://qtrypzzcjebvfcihiynt.supabase.co/storage/v1/object/public/base44-prod/apps/68eeb940-cdab-39e5-b866-85235b0baac9/res/tv-icon-512.png",
          type: "image/png",
          sizes: "512x512"
        }
      ],
      start_url: ".",
      display: "standalone",
      theme_color: "#EA580C",
      background_color: "#000000"
    };

    const manifestString = JSON.stringify(manifest);
    const blob = new Blob([manifestString], { type: 'application/json' });
    const manifestURL = URL.createObjectURL(blob);
    
    let link = document.querySelector("link[rel='manifest']");
    if (!link) {
        link = document.createElement('link');
        link.rel = 'manifest';
        document.head.appendChild(link);
    }
    link.href = manifestURL;

    return () => {
        if (manifestURL) {
            URL.revokeObjectURL(manifestURL);
        }
    };
  }, [currentPageName, navigate]);

  const handleIntroFinish = () => {
    setShowIntro(false);
    sessionStorage.setItem('introPlayed', 'true');
  };

  const handleLogout = () => {
    // Clear all local data and redirect to welcome
    try {
      localStorage.removeItem('playlists');
      navigate(createPageUrl('Welcome'), { replace: true });
      window.location.reload();
    } catch (error) {
      console.error('Logout error:', error);
    }
  };

  const navigationItems = [
    {
      title: "My Accounts",
      url: createPageUrl("Dashboard"),
      icon: Grid3x3,
    },
    {
      title: "Add Account",
      url: createPageUrl("AddAccount"),
      icon: Plus,
    },
  ];

  // Pages that don't need the sidebar layout
  const pagesWithoutSidebar = ['Welcome', 'SignUp'];
  
  if (showIntro) {
    return <IntroPlayer videoUrl={INTRO_VIDEO_URL} onFinished={handleIntroFinish} />;
  }

  // If on Welcome or SignUp page, render without sidebar
  if (pagesWithoutSidebar.includes(currentPageName)) {
    return children;
  }

  const isTV = deviceInfo?.isTV || false;
  const enableAnimations = perfConfig?.enableAnimations !== false;
  const enableBlur = perfConfig?.enableBlur !== false;

  // On TV devices, render without sidebar for full screen experience
  if (isTV) {
    return (
      <div className="min-h-screen flex w-full bg-gradient-to-br from-black to-gray-900">
        <style>{`
          :root {
            --background: 0 0% 0%;
            --foreground: 210 40% 98%;
            --card: 0 0% 0%;
            --card-foreground: 210 40% 98%;
            --popover: 0 0% 0%;
            --popover-foreground: 210 40% 98%;
            --primary: 24 95% 53%;
            --primary-foreground: 210 40% 98%;
            --secondary: 24 50% 20%;
            --secondary-foreground: 210 40% 98%;
            --muted: 24 30% 15%;
            --muted-foreground: 25 20% 65%;
            --accent: 24 50% 20%;
            --accent-foreground: 210 40% 98%;
            --destructive: 0 62.8% 30.6%;
            --destructive-foreground: 210 40% 98%;
            --border: 24 30% 15%;
            --input: 24 30% 15%;
            --ring: 24 95% 53%;
            --radius: 0.5rem;
          }
          * {
            -webkit-transform: translateZ(0);
            transform: translateZ(0);
            -webkit-backface-visibility: hidden;
            backface-visibility: hidden;
          }
          .tv-focusable {
            cursor: pointer;
            transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease !important;
          }
          .tv-focusable:focus,
          .tv-focusable:hover {
            transform: scale(1.08);
            box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.5);
            border-color: rgb(251, 146, 60);
            outline: none;
          }
        `}</style>
        
        <main className="flex-1 flex flex-col overflow-x-hidden">
          <div className="flex-1 overflow-auto">
            {children}
          </div>
        </main>
      </div>
    );
  }

  return (
    <SidebarProvider>
      <div className={`min-h-screen flex w-full ${isTV ? 'bg-gradient-to-br from-black to-gray-900' : 'bg-gradient-to-br from-black via-orange-950 to-black'}`}>
        <style>{`
          :root {
            --background: 0 0% 0%;
            --foreground: 210 40% 98%;
            --card: 0 0% 0%;
            --card-foreground: 210 40% 98%;
            --popover: 0 0% 0%;
            --popover-foreground: 210 40% 98%;
            --primary: 24 95% 53%;
            --primary-foreground: 210 40% 98%;
            --secondary: 24 50% 20%;
            --secondary-foreground: 210 40% 98%;
            --muted: 24 30% 15%;
            --muted-foreground: 25 20% 65%;
            --accent: 24 50% 20%;
            --accent-foreground: 210 40% 98%;
            --destructive: 0 62.8% 30.6%;
            --destructive-foreground: 210 40% 98%;
            --border: 24 30% 15%;
            --input: 24 30% 15%;
            --ring: 24 95% 53%;
            --radius: 0.5rem;
          }
          ${isTV ? `
            * {
              -webkit-transform: translateZ(0);
              transform: translateZ(0);
              -webkit-backface-visibility: hidden;
              backface-visibility: hidden;
            }
            .tv-focusable {
              cursor: pointer;
              transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease !important;
            }
            .tv-focusable:focus,
            .tv-focusable:hover {
              transform: scale(1.08);
              box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.5);
              border-color: rgb(251, 146, 60);
              outline: none;
            }
          ` : ''}
        `}</style>
        
        <Sidebar className={`border-r border-orange-500/20 ${enableBlur ? 'bg-gray-900/95 backdrop-blur-xl' : 'bg-gray-900'}`}>
          <SidebarHeader className="border-b border-orange-500/20 p-6">
            <div className="flex items-center justify-center">
              <div className="w-full bg-black rounded-xl p-4 shadow-lg shadow-orange-500/20 relative">
                <img src={LOGO_URL} alt="HushTV" className="w-full h-auto" />
                {/* Christmas Hat */}
                <div className="absolute -top-3 left-1/2 transform -translate-x-1/2 -rotate-12">
                  <svg width="60" height="50" viewBox="0 0 60 50" fill="none" xmlns="http://www.w3.org/2000/svg">
                    {/* Hat body */}
                    <path d="M10 35 L30 5 L50 35 Z" fill="#DC2626" />
                    {/* Hat trim */}
                    <ellipse cx="30" cy="35" rx="22" ry="4" fill="#FFFFFF" />
                    {/* Pom-pom */}
                    <circle cx="30" cy="5" r="6" fill="#FFFFFF" />
                    {/* Hat shadow/details */}
                    <path d="M10 35 L30 5 L50 35 Z" fill="#B91C1C" opacity="0.3" />
                  </svg>
                </div>
              </div>
            </div>
          </SidebarHeader>
          
          <SidebarContent className="p-3 flex-grow">
            {navigationItems.map((item) => (
              <Link
                to={item.url}
                key={item.title} 
                className={`flex items-center gap-3 px-4 ${isTV ? 'py-4' : 'py-3'} hover:bg-orange-500/20 hover:text-orange-300 ${enableAnimations ? 'transition-all duration-200' : ''} rounded-lg mb-1 ${isTV ? 'tv-focusable' : ''} ${
                  location.pathname === item.url ? 'bg-orange-500/30 text-orange-300 shadow-lg shadow-orange-500/20' : 'text-gray-300'
                }`}
              >
                <item.icon className={`${isTV ? 'w-6 h-6' : 'w-5 h-5'}`} />
                <span className={`font-medium ${isTV ? 'text-lg' : ''}`}>{item.title}</span>
              </Link>
            ))}
          </SidebarContent>

          <div className="border-t border-orange-500/20 p-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-gradient-to-br from-orange-500 to-orange-700 rounded-full flex items-center justify-center shadow-lg">
                  <User className="w-5 h-5 text-white" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-medium text-white text-sm">Guest User</p>
                  <p className="text-xs text-orange-300">HushTV Player</p>
                </div>
              </div>
              <button
                onClick={handleLogout}
                className="p-2 hover:bg-orange-500/20 rounded-lg transition-colors"
                title="Clear all data"
              >
                <LogOut className="w-4 h-4 text-gray-400" />
              </button>
            </div>
          </div>
        </Sidebar>

        <main className="flex-1 flex flex-col overflow-x-hidden">
          <header className={`${enableBlur ? 'bg-gray-900/50 backdrop-blur-xl' : 'bg-gray-900'} border-b border-orange-500/20 px-6 py-4 md:hidden`}>
            <div className="flex items-center gap-4">
              <SidebarTrigger className="hover:bg-orange-500/20 p-2 rounded-lg transition-colors" />
              <div className="h-8 w-32 relative">
                <img src={LOGO_URL} alt="HushTV" className="h-full w-auto" />
                {/* Christmas Hat for mobile */}
                <div className="absolute -top-1 left-1/2 transform -translate-x-1/2 -rotate-12">
                  <svg width="30" height="25" viewBox="0 0 60 50" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M10 35 L30 5 L50 35 Z" fill="#DC2626" />
                    <ellipse cx="30" cy="35" rx="22" ry="4" fill="#FFFFFF" />
                    <circle cx="30" cy="5" r="6" fill="#FFFFFF" />
                    <path d="M10 35 L30 5 L50 35 Z" fill="#B91C1C" opacity="0.3" />
                  </svg>
                </div>
              </div>
            </div>
          </header>

          <div className="flex-1 overflow-auto">
            {children}
          </div>
        </main>
      </div>
    </SidebarProvider>
  );
}