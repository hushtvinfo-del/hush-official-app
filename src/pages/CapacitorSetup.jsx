import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, CheckCircle, Copy, AlertTriangle, Tv, Download, Terminal, Package, ExternalLink } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

// The live URL of your HushTV web app
const APP_URL = window.location.origin;
const TV_URL = `${APP_URL}/tv`;

const CodeBlock = ({ children }) => {
  const [copied, setCopied] = useState(false);
  return (
    <div className="relative group my-3">
      <pre className="bg-gray-950 p-4 rounded-lg overflow-x-auto border border-cyan-500/20 text-sm">
        <code className="text-green-300 whitespace-pre-wrap break-all">{children}</code>
      </pre>
      <button
        onClick={() => { navigator.clipboard.writeText(children); setCopied(true); setTimeout(() => setCopied(false), 2000); }}
        className="absolute top-2 right-2 flex items-center gap-1 px-3 py-1.5 rounded bg-gray-700 hover:bg-gray-600 text-xs text-white opacity-0 group-hover:opacity-100 transition-opacity"
      >
        {copied ? <><CheckCircle className="w-3 h-3 text-green-400" /> Copied!</> : <><Copy className="w-3 h-3" /> Copy</>}
      </button>
    </div>
  );
};

const Step = ({ number, title, children, color = 'blue' }) => {
  const colors = {
    blue: 'from-blue-600 to-cyan-600 border-blue-500/30',
    green: 'from-green-600 to-emerald-600 border-green-500/30',
    purple: 'from-purple-600 to-pink-600 border-purple-500/30',
    orange: 'from-orange-600 to-red-600 border-orange-500/30',
  };
  return (
    <Card className={`bg-gray-900/60 border mb-6 ${colors[color].split(' ')[2]}`}>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-3 text-white text-lg">
          <div className={`w-9 h-9 bg-gradient-to-br ${colors[color].split(' ')[0]} ${colors[color].split(' ')[1]} rounded-full flex items-center justify-center font-black text-base flex-shrink-0`}>
            {number}
          </div>
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="text-gray-300 space-y-3 text-sm leading-relaxed">
        {children}
      </CardContent>
    </Card>
  );
};

const Pill = ({ children }) => (
  <span className="inline-block bg-gray-800 border border-gray-600 text-cyan-300 px-2 py-0.5 rounded font-mono text-xs">{children}</span>
);

export default function CapacitorSetup() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen p-4 md:p-8 bg-gray-950">
      <div className="max-w-3xl mx-auto">
        <Button variant="ghost" onClick={() => navigate(-1)} className="mb-6 text-cyan-300 hover:text-white hover:bg-cyan-500/20">
          <ArrowLeft className="mr-2 h-4 w-4" /> Back
        </Button>

        <div className="mb-8">
          <h1 className="text-4xl font-bold text-white mb-2 flex items-center gap-3">
            <Tv className="w-10 h-10 text-cyan-400" />
            Build Android TV APK
          </h1>
          <p className="text-cyan-300 text-lg">Step-by-step guide — no coding experience needed</p>
        </div>

        <Alert className="mb-8 bg-cyan-900/20 border-cyan-500/40">
          <AlertDescription className="text-cyan-200 text-sm">
            <strong>📺 What you'll end up with:</strong> An APK file you can sideload onto your NVIDIA Shield, Fire Stick, or any Android TV device. The app will open directly to the TV interface at <span className="font-mono text-cyan-400">/tv</span>.
          </AlertDescription>
        </Alert>

        {/* Your app URL */}
        <Card className="bg-blue-950/40 border-blue-500/30 mb-8">
          <CardContent className="pt-4">
            <p className="text-white font-semibold mb-2 text-sm">📌 Your TV App URL (you'll need this in Step 4):</p>
            <div className="bg-gray-950 px-4 py-3 rounded-lg border border-blue-500/30 font-mono text-cyan-300 text-sm break-all flex items-center justify-between gap-3">
              <span>{TV_URL}</span>
              <button
                onClick={() => navigator.clipboard.writeText(TV_URL)}
                className="flex-shrink-0 text-gray-400 hover:text-white"
              >
                <Copy className="w-4 h-4" />
              </button>
            </div>
          </CardContent>
        </Card>

        {/* OPTION A: Easy way */}
        <div className="mb-6">
          <div className="flex items-center gap-3 mb-4">
            <div className="h-px flex-1 bg-gradient-to-r from-transparent to-green-500/40" />
            <span className="text-green-400 font-bold text-lg px-3">⭐ Easiest Method — No Setup Required</span>
            <div className="h-px flex-1 bg-gradient-to-l from-transparent to-green-500/40" />
          </div>

          <Step number="A" title="Use a Free Online APK Builder (Recommended)" color="green">
            <p>You don't need to install anything. These free services build an APK from a URL in minutes:</p>
            
            <div className="space-y-4 mt-4">
              <div className="bg-green-950/30 border border-green-500/30 rounded-lg p-4">
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div>
                    <p className="text-white font-bold text-base">Option 1: WebViewGold / AppMySite</p>
                    <p className="text-gray-400 text-xs">Paid but very easy — generates a proper signed APK</p>
                  </div>
                  <a href="https://appsgeyser.com" target="_blank" rel="noopener noreferrer" className="text-green-400 hover:text-green-300 flex items-center gap-1 text-xs flex-shrink-0">
                    Visit <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
              </div>

              <div className="bg-green-950/30 border border-green-500/30 rounded-lg p-4">
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div>
                    <p className="text-white font-bold text-base">Option 2: AppsGeyser (Free)</p>
                    <p className="text-gray-400 text-xs">Completely free — builds a WebView APK around your URL</p>
                  </div>
                  <a href="https://appsgeyser.com/create/website" target="_blank" rel="noopener noreferrer" className="text-green-400 hover:text-green-300 flex items-center gap-1 text-xs flex-shrink-0">
                    Visit <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
                <ol className="list-decimal list-inside space-y-2 text-gray-300 text-xs pl-2">
                  <li>Go to <strong className="text-white">appsgeyser.com/create/website</strong></li>
                  <li>Paste your TV URL: <span className="font-mono text-cyan-300 break-all">{TV_URL}</span></li>
                  <li>Set App Name: <strong className="text-white">HushTV</strong></li>
                  <li>Click <strong className="text-white">Create</strong> → Download APK</li>
                  <li>Transfer APK to your Android TV and install it</li>
                </ol>
              </div>

              <div className="bg-green-950/30 border border-green-500/30 rounded-lg p-4">
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div>
                    <p className="text-white font-bold text-base">Option 3: Gonative.io</p>
                    <p className="text-gray-400 text-xs">Free trial, best quality WebView wrapper</p>
                  </div>
                  <a href="https://gonative.io" target="_blank" rel="noopener noreferrer" className="text-green-400 hover:text-green-300 flex items-center gap-1 text-xs flex-shrink-0">
                    Visit <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
                <ol className="list-decimal list-inside space-y-2 text-gray-300 text-xs pl-2">
                  <li>Go to <strong className="text-white">gonative.io</strong> → New App</li>
                  <li>Enter your TV URL: <span className="font-mono text-cyan-300 break-all">{TV_URL}</span></li>
                  <li>Download the Android APK from the build results</li>
                </ol>
              </div>
            </div>

            <Alert className="mt-4 bg-yellow-900/20 border-yellow-500/30">
              <AlertDescription className="text-yellow-200 text-xs">
                <strong>Installing on Android TV:</strong> Enable "Unknown Sources" in your TV's Developer Settings first, then use a file manager app or ADB to install the APK.
              </AlertDescription>
            </Alert>
          </Step>
        </div>

        {/* OPTION B: Manual */}
        <div>
          <div className="flex items-center gap-3 mb-4">
            <div className="h-px flex-1 bg-gradient-to-r from-transparent to-blue-500/40" />
            <span className="text-blue-400 font-bold text-lg px-3">🛠 Manual Method — Full Control</span>
            <div className="h-px flex-1 bg-gradient-to-l from-transparent to-blue-500/40" />
          </div>

          <Alert className="mb-5 bg-blue-900/20 border-blue-500/30">
            <AlertDescription className="text-blue-200 text-xs">
              This method requires a Windows/Mac/Linux computer. You only need to do this setup once. Takes about 30–60 minutes the first time.
            </AlertDescription>
          </Alert>

          <Step number={1} title="Install the tools you need" color="blue">
            <p>Download and install these two things (both free):</p>
            <div className="space-y-3 mt-3">
              <div className="flex items-center justify-between bg-gray-800 rounded-lg px-4 py-3">
                <div>
                  <p className="text-white font-semibold">Node.js</p>
                  <p className="text-gray-400 text-xs">Choose the "LTS" version</p>
                </div>
                <a href="https://nodejs.org" target="_blank" rel="noopener noreferrer" className="text-cyan-400 hover:text-cyan-300 text-xs flex items-center gap-1">
                  Download <ExternalLink className="w-3 h-3" />
                </a>
              </div>
              <div className="flex items-center justify-between bg-gray-800 rounded-lg px-4 py-3">
                <div>
                  <p className="text-white font-semibold">Android Studio</p>
                  <p className="text-gray-400 text-xs">Needed to build the APK</p>
                </div>
                <a href="https://developer.android.com/studio" target="_blank" rel="noopener noreferrer" className="text-cyan-400 hover:text-cyan-300 text-xs flex items-center gap-1">
                  Download <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            </div>
            <p className="text-gray-400 text-xs mt-3">After installing Android Studio, open it and let it finish downloading the Android SDK (it will prompt you automatically).</p>
          </Step>

          <Step number={2} title="Create a new project folder on your computer" color="blue">
            <p>Open <strong className="text-white">Terminal</strong> (Mac/Linux) or <strong className="text-white">Command Prompt</strong> (Windows) and run:</p>
            <CodeBlock>{`mkdir hushtv-tv-app
cd hushtv-tv-app
npm init -y`}</CodeBlock>
            <p>Then install Capacitor:</p>
            <CodeBlock>{`npm install @capacitor/core @capacitor/cli @capacitor/android`}</CodeBlock>
          </Step>

          <Step number={3} title="Initialize the Android project" color="blue">
            <p>Still in the same folder, run:</p>
            <CodeBlock>{`npx cap init "HushTV" "com.hushtv.tv" --web-dir=www`}</CodeBlock>
            <p>Create the web folder and a simple index file:</p>
            <CodeBlock>{`mkdir www
echo '<!DOCTYPE html><html><head><meta http-equiv="refresh" content="0; url=${TV_URL}"></head><body></body></html>' > www/index.html`}</CodeBlock>
          </Step>

          <Step number={4} title="Set your TV URL in the Capacitor config" color="blue">
            <p>Open the file <Pill>capacitor.config.json</Pill> that was just created and replace everything in it with this:</p>
            <CodeBlock>{`{
  "appId": "com.hushtv.tv",
  "appName": "HushTV",
  "webDir": "www",
  "server": {
    "url": "${TV_URL}",
    "cleartext": true,
    "androidScheme": "https"
  },
  "android": {
    "allowMixedContent": true,
    "captureInput": true,
    "webContentsDebuggingEnabled": false
  }
}`}</CodeBlock>
            <Alert className="bg-cyan-900/20 border-cyan-500/30">
              <AlertDescription className="text-cyan-200 text-xs">
                ✅ The <Pill>server.url</Pill> setting is the key part — it tells the Android app to load your HushTV TV interface directly, without any local files.
              </AlertDescription>
            </Alert>
          </Step>

          <Step number={5} title="Add Android and open Android Studio" color="blue">
            <CodeBlock>{`npx cap add android
npx cap open android`}</CodeBlock>
            <p>Android Studio will open. <strong className="text-white">Wait for the Gradle sync to finish</strong> (progress bar at the bottom — can take 2–5 minutes).</p>
          </Step>

          <Step number={6} title="Build the APK" color="orange">
            <p>In Android Studio, from the top menu:</p>
            <ol className="list-decimal list-inside space-y-2 pl-2 text-gray-300">
              <li>Click <strong className="text-white">Build</strong></li>
              <li>Click <strong className="text-white">Build Bundle(s) / APK(s)</strong></li>
              <li>Click <strong className="text-white">Build APK(s)</strong></li>
              <li>Wait for it to finish (1–3 minutes)</li>
              <li>A blue notification will appear at the bottom — click <strong className="text-white">"locate"</strong></li>
            </ol>
            <p className="mt-3">Your APK will be at:</p>
            <CodeBlock>android/app/build/outputs/apk/debug/app-debug.apk</CodeBlock>
            <Alert className="bg-green-900/20 border-green-500/30">
              <AlertDescription className="text-green-200 text-xs">
                🎉 That's your APK file! Copy it to a USB drive or send it to your Android TV to install.
              </AlertDescription>
            </Alert>
          </Step>

          <Step number={7} title="Install on your Android TV / Fire Stick" color="purple">
            <div className="space-y-4">
              <div>
                <p className="text-white font-semibold mb-2">📺 NVIDIA Shield / Android TV Box:</p>
                <ol className="list-decimal list-inside space-y-1.5 pl-2 text-gray-300 text-xs">
                  <li>Go to <strong className="text-white">Settings → Device Preferences → Security &amp; Restrictions</strong></li>
                  <li>Enable <strong className="text-white">Unknown Sources</strong></li>
                  <li>Copy the APK to a USB drive, plug it in to your TV</li>
                  <li>Use the <strong className="text-white">File Commander</strong> or <strong className="text-white">ES File Explorer</strong> app to find and tap the APK</li>
                  <li>Tap <strong className="text-white">Install</strong></li>
                </ol>
              </div>
              <div>
                <p className="text-white font-semibold mb-2">🔥 Fire TV Stick:</p>
                <ol className="list-decimal list-inside space-y-1.5 pl-2 text-gray-300 text-xs">
                  <li>Go to <strong className="text-white">Settings → My Fire TV → Developer Options</strong></li>
                  <li>Enable <strong className="text-white">Apps from Unknown Sources</strong></li>
                  <li>Install the free <strong className="text-white">Downloader</strong> app from the Amazon App Store</li>
                  <li>Open Downloader and enter the URL where your APK is hosted (e.g. upload it to Google Drive and share the link)</li>
                  <li>Download and install</li>
                </ol>
              </div>
              <div>
                <p className="text-white font-semibold mb-2">🖥 ADB Install (any Android TV, easiest if you have a PC):</p>
                <CodeBlock>{`# Connect your TV and PC to same WiFi, enable ADB on TV, then:
adb connect YOUR_TV_IP_ADDRESS
adb install app-debug.apk`}</CodeBlock>
              </div>
            </div>
          </Step>
        </div>

        {/* Summary */}
        <Card className="bg-gradient-to-br from-cyan-950/40 to-blue-950/40 border-cyan-500/30 mt-4">
          <CardContent className="pt-5">
            <h3 className="text-white font-bold text-lg mb-4 flex items-center gap-2"><CheckCircle className="w-5 h-5 text-green-400" /> Quick Summary</h3>
            <div className="space-y-2 text-sm text-gray-300">
              <p>⭐ <strong className="text-white">Fastest (5 min):</strong> Use AppsGeyser.com — paste your URL, download APK</p>
              <p>🛠 <strong className="text-white">Best quality:</strong> Manual method with Capacitor — full control, no watermarks</p>
              <p>📺 <strong className="text-white">Your TV URL:</strong> <span className="font-mono text-cyan-400 text-xs break-all">{TV_URL}</span></p>
              <p>📱 <strong className="text-white">Installing:</strong> Enable "Unknown Sources" on your TV device first</p>
            </div>
          </CardContent>
        </Card>

        <div className="mt-6 text-center">
          <Button onClick={() => navigate(-1)} className="bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-700 hover:to-blue-700" size="lg">
            Done — Back to App 🚀
          </Button>
        </div>
      </div>
    </div>
  );
}