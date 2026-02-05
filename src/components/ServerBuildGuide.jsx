import React from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Copy, Server, Package, Smartphone } from "lucide-react";
import { Button } from "@/components/ui/button";

const CodeBlock = ({ code, title }) => {
  const handleCopy = () => {
    navigator.clipboard.writeText(code);
  };

  return (
    <div className="relative group mb-4">
      {title && <p className="text-sm text-cyan-400 mb-2 font-semibold">{title}</p>}
      <pre className="bg-slate-950 border border-blue-500/30 p-4 rounded-lg overflow-x-auto text-sm text-gray-300">
        <code>{code}</code>
      </pre>
      <Button
        onClick={handleCopy}
        size="sm"
        variant="ghost"
        className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity"
      >
        <Copy className="w-4 h-4" />
      </Button>
    </div>
  );
};

export default function ServerBuildGuide() {
  const setupScript = `#!/bin/bash
# HushTV APK Build Script
# Run this on your server: bash build-hushtv.sh

echo "🚀 HushTV APK Builder"
echo "===================="

# Install dependencies
echo "📦 Installing dependencies..."
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
apt-get install -y nodejs openjdk-17-jdk wget unzip

# Install Android SDK Command Line Tools
echo "📱 Setting up Android SDK..."
mkdir -p /opt/android-sdk/cmdline-tools
cd /opt/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip commandlinetools-linux-9477386_latest.zip
mv cmdline-tools latest

export ANDROID_HOME=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept licenses
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.0"

# Create project directory
echo "📂 Creating project..."
mkdir -p ~/hushtv-build
cd ~/hushtv-build

# Download your code from Base44
echo "⬇️ Export your code from Base44 dashboard first!"
echo "Then upload the ZIP to your server and extract it here"
read -p "Press enter when code is ready in ~/hushtv-build..."

# Install dependencies
echo "📦 Installing Node packages..."
npm install

# Install Capacitor
echo "⚡ Installing Capacitor..."
npm install @capacitor/core @capacitor/cli @capacitor/android

# Initialize Capacitor
echo "🔧 Initializing Capacitor..."
npx cap init "HushTV" "com.hushtv.app" --web-dir=dist

# Add Android platform
echo "🤖 Adding Android platform..."
npx cap add android

# Build web app
echo "🏗️ Building web app..."
npm run build

# Sync to Android
echo "🔄 Syncing to Android..."
npx cap sync android

# Build APK
echo "📦 Building APK..."
cd android
./gradlew assembleRelease

echo "✅ APK built successfully!"
echo "📍 Location: android/app/build/outputs/apk/release/app-release-unsigned.apk"
echo ""
echo "To sign the APK, you'll need to create a keystore and run:"
echo "jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore my-release-key.keystore app-release-unsigned.apk alias_name"`;

  const quickCommands = `# Quick start commands
cd ~
mkdir hushtv-build
cd hushtv-build

# 1. Upload your exported Base44 code ZIP here
# 2. Extract it: unzip hushtv-code.zip

# 3. Run the build script
bash build-hushtv.sh`;

  return (
    <div className="min-h-screen p-6 max-w-5xl mx-auto">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white mb-2 flex items-center gap-3">
          <Server className="w-8 h-8 text-blue-400" />
          Server APK Build Guide
        </h1>
        <p className="text-cyan-300">Build your HushTV Android APK on your server</p>
      </div>

      <div className="space-y-6">
        <Card className="bg-slate-900/50 border-blue-500/30">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-white">
              <Package className="w-5 h-5 text-blue-400" />
              Step 1: Export Your Code
            </CardTitle>
          </CardHeader>
          <CardContent className="text-gray-300 space-y-3">
            <p>1. Go to Base44 Dashboard → Code → Export</p>
            <p>2. Download the ZIP file containing your app code</p>
            <p>3. Upload it to your server at <code className="bg-slate-950 px-2 py-1 rounded">~/hushtv-build/</code></p>
          </CardContent>
        </Card>

        <Card className="bg-slate-900/50 border-blue-500/30">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-white">
              <Server className="w-5 h-5 text-blue-400" />
              Step 2: Create Build Script
            </CardTitle>
          </CardHeader>
          <CardContent className="text-gray-300 space-y-3">
            <p>SSH into your server and create this script:</p>
            <CodeBlock 
              code={`nano ~/build-hushtv.sh`}
              title="Create the script file"
            />
            <p>Paste the following script content:</p>
            <CodeBlock code={setupScript} />
            <CodeBlock 
              code={`chmod +x ~/build-hushtv.sh`}
              title="Make it executable"
            />
          </CardContent>
        </Card>

        <Card className="bg-slate-900/50 border-blue-500/30">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-white">
              <Smartphone className="w-5 h-5 text-blue-400" />
              Step 3: Build the APK
            </CardTitle>
          </CardHeader>
          <CardContent className="text-gray-300 space-y-3">
            <CodeBlock code={quickCommands} title="Run these commands" />
            <div className="bg-blue-500/10 border border-blue-500/30 rounded-lg p-4 mt-4">
              <p className="text-sm text-cyan-300">
                ⏱️ Build time: ~10-15 minutes<br/>
                📦 Output: <code className="bg-slate-950 px-2 py-1 rounded">android/app/build/outputs/apk/release/app-release-unsigned.apk</code>
              </p>
            </div>
          </CardContent>
        </Card>

        <Card className="bg-slate-900/50 border-yellow-500/30">
          <CardHeader>
            <CardTitle className="text-white">⚠️ Important Notes</CardTitle>
          </CardHeader>
          <CardContent className="text-gray-300 space-y-2">
            <p>• The APK will be <strong>unsigned</strong> - you need to sign it before distribution</p>
            <p>• For Google Play Store, you need a signed release build</p>
            <p>• Keep your keystore file safe - losing it means you can't update your app</p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}