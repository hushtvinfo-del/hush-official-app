import React from "react";
import { Link } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Card, CardContent } from "@/components/ui/card";
import { UserPlus, LogIn } from "lucide-react";
import { motion } from "framer-motion";

const LOGO_URL = "https://qtrypzzcjebvfcihiynt.supabase.co/storage/v1/object/public/base44-prod/public/68eeb940cdab39e5b8668523/ea04a0d3e_HushTVLogo.png";

export default function Welcome() {
  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-black">
      <div className="w-full max-w-4xl">
        <motion.div 
          initial={{ opacity: 0, y: -30 }} 
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="text-center mb-12"
        >
          <div className="w-full max-w-md mx-auto mb-6 bg-slate-950 rounded-xl p-8 shadow-2xl shadow-blue-500/20">
            <img src={LOGO_URL} alt="HushTV" className="w-full h-auto" />
          </div>
          <h2 className="text-2xl md:text-3xl font-bold text-white mb-2">Web Player</h2>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 md:gap-8">
          <motion.div
            initial={{ opacity: 0, x: -50 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
          >
            <a href="https://hushtv.org/shop" target="_blank" rel="noopener noreferrer">
              <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 hover:bg-slate-800/70 transition-all cursor-pointer group h-full">
                <CardContent className="p-8 md:p-12 flex flex-col items-center justify-center text-center h-full">
                  <div className="w-20 h-20 bg-gradient-to-br from-blue-500/20 to-blue-800/20 rounded-full flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    <UserPlus className="w-10 h-10 text-blue-400" />
                  </div>
                  <h3 className="text-2xl md:text-3xl font-bold text-white mb-2 group-hover:text-cyan-300 transition-colors">
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
              <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 hover:bg-slate-800/70 transition-all cursor-pointer group h-full">
                <CardContent className="p-8 md:p-12 flex flex-col items-center justify-center text-center h-full">
                  <div className="w-20 h-20 bg-gradient-to-br from-blue-500/20 to-blue-800/20 rounded-full flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    <LogIn className="w-10 h-10 text-blue-400" />
                  </div>
                  <h3 className="text-2xl md:text-3xl font-bold text-white mb-2 group-hover:text-cyan-300 transition-colors">
                    Sign In To HushTV
                  </h3>
                  <p className="text-gray-400 text-sm md:text-base">Access your account</p>
                </CardContent>
              </Card>
            </Link>
          </motion.div>
        </div>
      </div>
    </div>
  );
}