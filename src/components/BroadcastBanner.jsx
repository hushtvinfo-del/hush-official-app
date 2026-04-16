import React from 'react';
import { X, AlertTriangle, Info, Bell, Wrench } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

const TYPE_CONFIG = {
    info: { icon: Info, bg: 'bg-blue-600', border: 'border-blue-400', text: 'text-blue-100' },
    warning: { icon: AlertTriangle, bg: 'bg-yellow-600', border: 'border-yellow-400', text: 'text-yellow-100' },
    alert: { icon: Bell, bg: 'bg-red-600', border: 'border-red-400', text: 'text-red-100' },
    maintenance: { icon: Wrench, bg: 'bg-orange-600', border: 'border-orange-400', text: 'text-orange-100' },
};

export default function BroadcastBanner({ message, onDismiss }) {
    if (!message) return null;

    const config = TYPE_CONFIG[message.type] || TYPE_CONFIG.info;
    const Icon = config.icon;

    return (
        <AnimatePresence>
            <motion.div
                initial={{ y: -100, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                exit={{ y: -100, opacity: 0 }}
                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                className={`fixed top-0 left-0 right-0 z-50 ${config.bg} border-b-2 ${config.border} shadow-2xl`}
            >
                <div className="max-w-6xl mx-auto px-4 py-3 flex items-center gap-3">
                    <div className="flex-shrink-0">
                        <Icon className="w-5 h-5 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                        {message.title && (
                            <span className="font-bold text-white mr-2">{message.title}:</span>
                        )}
                        <span className={`text-sm ${config.text}`}>{message.message}</span>
                    </div>
                    <button
                        onClick={onDismiss}
                        className="flex-shrink-0 p-1 rounded hover:bg-black/20 transition-colors"
                    >
                        <X className="w-4 h-4 text-white" />
                    </button>
                </div>
            </motion.div>
        </AnimatePresence>
    );
}