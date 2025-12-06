import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Smartphone, Download, CheckCircle, Copy, Terminal, Package, Play, Code } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

const CodeBlock = ({ children, language = 'bash' }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(children);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="relative group">
      <pre className="bg-gray-950 p-4 rounded-lg overflow-x-auto border border-orange-500/20">
        <code className="text-sm text-green-300">{children}</code>
      </pre>
      <Button
        size="sm"
        variant="ghost"
        onClick={handleCopy}
        className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity bg-gray-800/80 hover:bg-gray-700"
      >
        {copied ? (
          <>
            <CheckCircle className="w-4 h-4 mr-2 text-green-400" />
            <span className="text-green-400">Copied!</span>
          </>
        ) : (
          <>
            <Copy className="w-4 h-4 mr-2" />
            Copy
          </>
        )}
      </Button>
    </div>
  );
};

const Step = ({ number, title, children, icon: Icon }) => (
  <Card className="bg-gray-800/50 border-orange-500/30 mb-6">
    <CardHeader>
      <CardTitle className="flex items-center gap-3 text-white">
        <div className="w-10 h-10 bg-gradient-to-br from-orange-600 to-orange-800 rounded-full flex items-center justify-center font-bold text-lg">
          {number}
        </div>
        <div className="flex items-center gap-2">
          {Icon && <Icon className="w-5 h-5 text-orange-400" />}
          {title}
        </div>
      </CardTitle>
    </CardHeader>
    <CardContent className="text-gray-300 space-y-4">
      {children}
    </CardContent>
  </Card>
);

export default function CapacitorSetup() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-5xl mx-auto">
        <Button 
          variant="ghost" 
          onClick={() => navigate(-1)} 
          className="mb-6 text-orange-300 hover:text-white hover:bg-orange-500/20"
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back
        </Button>

        <div className="mb-8">
          <h1 className="text-4xl font-bold text-white mb-2 flex items-center gap-3">
            <Smartphone className="w-10 h-10 text-orange-400" />
            Capacitor Setup Guide
          </h1>
          <p className="text-orange-300 text-lg">Convert HushTV Web Player to Native iOS & Android Apps</p>
        </div>

        <Alert className="mb-6 bg-orange-900/20 border-orange-500/30">
          <AlertDescription className="text-orange-200">
            <strong>📱 What You'll Get:</strong> Native iOS and Android apps that can be published to App Store and Google Play Store, 
            with full access to device features and better performance than PWA.
          </AlertDescription>
        </Alert>

        <Step number={1} title="Prerequisites" icon={Package}>
          <p>Before starting, make sure you have the following installed on your development machine:</p>
          <ul className="list-disc list-inside space-y-2 pl-4 text-gray-300">
            <li><strong className="text-white">Node.js</strong> (v16 or higher) - <a href="https://nodejs.org" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">Download here</a></li>
            <li><strong className="text-white">Android Studio</strong> (for Android builds) - <a href="https://developer.android.com/studio" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">Download here</a></li>
            <li><strong className="text-white">Xcode</strong> (for iOS builds, Mac only) - <a href="https://apps.apple.com/app/xcode/id497799835" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">Download here</a></li>
            <li><strong className="text-white">Git</strong> - <a href="https://git-scm.com" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">Download here</a></li>
          </ul>
        </Step>

        <Step number={2} title="Download Your App Code" icon={Download}>
          <p>First, you need to export your app code from Base44:</p>
          <ol className="list-decimal list-inside space-y-3 pl-4">
            <li className="text-white">
              Go to your Base44 Dashboard → <strong>Code</strong> → <strong>Export</strong>
            </li>
            <li className="text-white">
              Click <strong>"Download Source Code"</strong> to get a ZIP file
            </li>
            <li className="text-white">
              Extract the ZIP file to a folder on your computer (e.g., <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">~/Projects/hushtv-app</code>)
            </li>
          </ol>
          <Alert className="mt-4 bg-blue-900/20 border-blue-500/30">
            <AlertDescription className="text-blue-200">
              💡 <strong>Tip:</strong> Choose a location without spaces in the path for better compatibility.
            </AlertDescription>
          </Alert>
        </Step>

        <Step number={3} title="Install Capacitor CLI" icon={Terminal}>
          <p>Open Terminal (Mac/Linux) or Command Prompt (Windows) and navigate to your app folder:</p>
          <CodeBlock>cd ~/Projects/hushtv-app</CodeBlock>
          
          <p className="mt-4">Install Capacitor and required dependencies:</p>
          <CodeBlock>{`npm install @capacitor/core @capacitor/cli
npm install @capacitor/android @capacitor/ios
npm install @capacitor/splash-screen @capacitor/status-bar`}</CodeBlock>
        </Step>

        <Step number={4} title="Initialize Capacitor" icon={Code}>
          <p>Initialize Capacitor in your project:</p>
          <CodeBlock>npx cap init "HushTV Player" "com.hushtv.player" --web-dir=build</CodeBlock>
          
          <p className="mt-4">This creates a <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">capacitor.config.json</code> file. Replace its contents with the optimized config:</p>
          <div className="bg-gray-900 p-4 rounded-lg border border-orange-500/20">
            <p className="text-sm text-gray-400 mb-2">📄 capacitor.config.json (already created in your project)</p>
            <p className="text-orange-300">✅ This file is already configured in your exported code!</p>
          </div>
        </Step>

        <Step number={5} title="Build Your Web App" icon={Package}>
          <p>Build the React app for production:</p>
          <CodeBlock>npm run build</CodeBlock>
          
          <Alert className="mt-4 bg-yellow-900/20 border-yellow-500/30">
            <AlertDescription className="text-yellow-200">
              ⚠️ <strong>Important:</strong> You must run <code className="bg-gray-900 px-2 py-1 rounded">npm run build</code> every time you make changes to your app code.
            </AlertDescription>
          </Alert>
        </Step>

        <Step number={6} title="Add iOS & Android Platforms" icon={Smartphone}>
          <p>Add native platforms to your project:</p>
          
          <div className="space-y-4">
            <div>
              <p className="font-semibold text-white mb-2">For Android:</p>
              <CodeBlock>npx cap add android</CodeBlock>
            </div>
            
            <div>
              <p className="font-semibold text-white mb-2">For iOS (Mac only):</p>
              <CodeBlock>npx cap add ios</CodeBlock>
            </div>
          </div>

          <p className="mt-4">Sync your web code to the native projects:</p>
          <CodeBlock>npx cap sync</CodeBlock>
        </Step>

        <Step number={7} title="Configure Android App" icon={Smartphone}>
          <p>Open Android Studio:</p>
          <CodeBlock>npx cap open android</CodeBlock>

          <div className="mt-4 space-y-3">
            <p className="font-semibold text-white">In Android Studio:</p>
            <ol className="list-decimal list-inside space-y-2 pl-4">
              <li>Wait for Gradle sync to complete</li>
              <li>Go to <strong className="text-white">Build</strong> → <strong className="text-white">Select Build Variant</strong> → Choose <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">release</code></li>
              <li>Update app icon: Replace files in <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">android/app/src/main/res/mipmap-*</code> folders</li>
              <li>Edit <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">android/app/src/main/res/values/strings.xml</code>:</li>
            </ol>
          </div>

          <CodeBlock language="xml">{`<resources>
    <string name="app_name">HushTV Player</string>
    <string name="title_activity_main">HushTV Player</string>
    <string name="package_name">com.hushtv.player</string>
    <string name="custom_url_scheme">com.hushtv.player</string>
</resources>`}</CodeBlock>

          <p className="mt-4">Build APK:</p>
          <ol className="list-decimal list-inside space-y-2 pl-4 mt-2">
            <li>Go to <strong className="text-white">Build</strong> → <strong className="text-white">Build Bundle(s) / APK(s)</strong> → <strong className="text-white">Build APK(s)</strong></li>
            <li>Wait for build to complete</li>
            <li>Click <strong className="text-white">"locate"</strong> to find your APK file</li>
            <li>APK location: <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">android/app/build/outputs/apk/release/app-release.apk</code></li>
          </ol>
        </Step>

        <Step number={8} title="Configure iOS App" icon={Smartphone}>
          <p>Open Xcode (Mac only):</p>
          <CodeBlock>npx cap open ios</CodeBlock>

          <div className="mt-4 space-y-3">
            <p className="font-semibold text-white">In Xcode:</p>
            <ol className="list-decimal list-inside space-y-2 pl-4">
              <li>Select <strong className="text-white">App</strong> target in the sidebar</li>
              <li>Update <strong className="text-white">Bundle Identifier</strong> to <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">com.hushtv.player</code></li>
              <li>Set <strong className="text-white">Team</strong> (requires Apple Developer account)</li>
              <li>Update app icon: Drag icon images to <strong className="text-white">Assets.xcassets/AppIcon</strong></li>
              <li>Go to <strong className="text-white">Signing & Capabilities</strong> → Enable <strong className="text-white">Automatically manage signing</strong></li>
            </ol>
          </div>

          <p className="mt-4">Build IPA:</p>
          <ol className="list-decimal list-inside space-y-2 pl-4 mt-2">
            <li>Select <strong className="text-white">Any iOS Device</strong> (or your connected iPhone) from device dropdown</li>
            <li>Go to <strong className="text-white">Product</strong> → <strong className="text-white">Archive</strong></li>
            <li>Wait for archive to complete</li>
            <li>Click <strong className="text-white">Distribute App</strong> → Choose distribution method</li>
          </ol>

          <Alert className="mt-4 bg-blue-900/20 border-blue-500/30">
            <AlertDescription className="text-blue-200">
              💡 <strong>Note:</strong> iOS apps require an Apple Developer account ($99/year) to publish to App Store or install on real devices.
            </AlertDescription>
          </Alert>
        </Step>

        <Step number={9} title="Testing Your Apps" icon={Play}>
          <div className="space-y-4">
            <div>
              <p className="font-semibold text-white mb-2">Test on Android:</p>
              <ul className="list-disc list-inside space-y-2 pl-4">
                <li>Connect Android device via USB with <strong className="text-white">USB Debugging</strong> enabled</li>
                <li>In Android Studio, click the <strong className="text-white">Run</strong> button (green play icon)</li>
                <li>Or install APK manually: <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">adb install app-release.apk</code></li>
              </ul>
            </div>

            <div>
              <p className="font-semibold text-white mb-2">Test on iOS:</p>
              <ul className="list-disc list-inside space-y-2 pl-4">
                <li>Connect iPhone/iPad via USB</li>
                <li>Select your device in Xcode</li>
                <li>Click the <strong className="text-white">Run</strong> button (play icon)</li>
              </ul>
            </div>
          </div>
        </Step>

        <Step number={10} title="Publishing to App Stores" icon={Download}>
          <div className="space-y-4">
            <div>
              <p className="font-semibold text-white mb-2">📱 Google Play Store (Android):</p>
              <ol className="list-decimal list-inside space-y-2 pl-4">
                <li>Create a <a href="https://play.google.com/console" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">Google Play Developer account</a> ($25 one-time fee)</li>
                <li>Generate signed APK/AAB in Android Studio (<strong className="text-white">Build</strong> → <strong className="text-white">Generate Signed Bundle/APK</strong>)</li>
                <li>Upload to Play Console</li>
                <li>Fill in app details, screenshots, privacy policy</li>
                <li>Submit for review (usually 1-3 days)</li>
              </ol>
            </div>

            <div>
              <p className="font-semibold text-white mb-2">🍎 Apple App Store (iOS):</p>
              <ol className="list-decimal list-inside space-y-2 pl-4">
                <li>Create an <a href="https://developer.apple.com" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">Apple Developer account</a> ($99/year)</li>
                <li>Create App ID in Apple Developer portal</li>
                <li>Archive app in Xcode and distribute</li>
                <li>Upload to App Store Connect</li>
                <li>Fill in app details, screenshots, privacy policy</li>
                <li>Submit for review (usually 1-7 days)</li>
              </ol>
            </div>
          </div>

          <Alert className="mt-4 bg-green-900/20 border-green-500/30">
            <AlertDescription className="text-green-200">
              ✅ <strong>Tip:</strong> Prepare high-quality screenshots (1080x1920 for Android, various sizes for iOS) and a compelling app description before submitting!
            </AlertDescription>
          </Alert>
        </Step>

        <Card className="bg-gradient-to-br from-orange-900/20 to-orange-800/10 border-orange-500/30 mt-8">
          <CardHeader>
            <CardTitle className="text-white flex items-center gap-2">
              <CheckCircle className="w-6 h-6 text-green-400" />
              Important Notes
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-gray-300">
            <p>📱 <strong className="text-white">Update Workflow:</strong> When you update your app, run <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">npm run build</code> then <code className="bg-gray-900 px-2 py-1 rounded text-orange-300">npx cap sync</code></p>
            <p>🔧 <strong className="text-white">Native Features:</strong> Capacitor gives you access to camera, push notifications, file system, and more via plugins</p>
            <p>🌐 <strong className="text-white">API URLs:</strong> Make sure all your backend functions are accessible via HTTPS</p>
            <p>📊 <strong className="text-white">App Size:</strong> Your APK will be around 15-30MB, IPA around 20-40MB</p>
            <p>⚡ <strong className="text-white">Performance:</strong> Native apps are 2-3x faster than PWA and feel more responsive</p>
          </CardContent>
        </Card>

        <div className="mt-8 p-6 bg-gray-800/30 border border-orange-500/20 rounded-lg">
          <h3 className="text-xl font-bold text-white mb-4">📚 Helpful Resources</h3>
          <div className="grid md:grid-cols-2 gap-4">
            <a href="https://capacitorjs.com/docs" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">
              → Capacitor Documentation
            </a>
            <a href="https://developer.android.com/studio/publish" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">
              → Android Publishing Guide
            </a>
            <a href="https://developer.apple.com/app-store/submissions/" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">
              → iOS Publishing Guide
            </a>
            <a href="https://capacitorjs.com/docs/plugins" target="_blank" rel="noopener noreferrer" className="text-orange-400 hover:text-orange-300">
              → Capacitor Plugins
            </a>
          </div>
        </div>

        <div className="mt-8 text-center">
          <Button
            onClick={() => navigate(-1)}
            className="bg-gradient-to-r from-orange-600 to-orange-800 hover:from-orange-700 hover:to-orange-900"
            size="lg"
          >
            Got it! Let's Build 🚀
          </Button>
        </div>
      </div>
    </div>
  );
}