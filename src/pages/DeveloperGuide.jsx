import React from 'react';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

const Section = ({ title, children }) => (
    <div className="mb-8">
        <h2 className="text-2xl font-bold text-white border-b-2 border-purple-800 pb-2 mb-4">{title}</h2>
        <div className="text-purple-200 space-y-4">{children}</div>
    </div>
);

const CodeBlock = ({ children }) => (
    <pre className="bg-gray-900/50 p-4 rounded-md overflow-x-auto">
        <code className="text-sm text-green-300">{children}</code>
    </pre>
);

export default function DeveloperGuide() {
    const navigate = useNavigate();

    return (
        <div className="min-h-screen p-4 md:p-8">
            <div className="max-w-7xl mx-auto">
                <Button variant="ghost" onClick={() => navigate(-1)} className="mb-6 text-purple-300 hover:text-white hover:bg-purple-900/30">
                    <ArrowLeft className="mr-2 h-4 w-4" />
                    Back
                </Button>
                <h1 className="text-4xl font-bold text-white mb-8">Developer Handover Guide</h1>

                <Section title="1. High-Level Architecture">
                    <p>The system is composed of two main parts:</p>
                    <ul className="list-disc list-inside space-y-2 pl-4">
                        <li><strong>The Frontend (React Web App):</strong> This is the user interface built with React, TailwindCSS, and various JavaScript libraries. It is responsible for displaying the UI and communicating with the backend.</li>
                        <li><strong>The Backend (Serverless Functions & Database):</strong> This is the "brain" of the application. It handles all heavy lifting: communicating with the IPTV provider, fetching metadata from TMDB, performing AI searches, and storing user data.</li>
                    </ul>
                    <p className="font-bold mt-4">Key Insight: A native Android app will not re-use the React frontend code directly. Instead, it will have its own native UI (built in Kotlin/Jetpack Compose) and will communicate with the *same backend* that the web app uses.</p>
                </Section>

                <Section title="2. How to Build the Android APK">
                    <p>A developer should follow these steps to create the native Android application:</p>
                    <h3 className="text-lg font-semibold text-purple-100 mt-4">Step 1: Set Up an Android Project</h3>
                    <p>Create a new Android project using Android Studio. The recommended language is <strong>Kotlin</strong>. The UI should be built with <strong>Jetpack Compose</strong>.</p>
                    
                    <h3 className="text-lg font-semibold text-purple-100 mt-4">Step 2: Replicate the User Interface</h3>
                    <p>Recreate the pages and components from the web app as native Android screens. The file list below provides the blueprint for this. For example, `pages/Dashboard.js` becomes `DashboardScreen.kt`.</p>

                    <h3 className="text-lg font-semibold text-purple-100 mt-4">Step 3: Connect to the Backend Functions</h3>
                    <p>This is the most critical step. The Android app will make HTTP requests to the existing backend functions. The endpoints for these functions can be found in your Base44 dashboard under <strong>Code -&gt; Functions</strong>.</p>
                    <p>For example, to get movie categories, the app will make a POST request to the `xtreamProxy` function endpoint with a body like this:</p>
                    <CodeBlock>{`{
  "host": "your_provider_host",
  "username": "your_username",
  "password": "your_password",
  "params": {
    "action": "get_vod_categories"
  }
}`}</CodeBlock>
                    <p>Use a networking library like <strong>Retrofit</strong> or <strong>Ktor</strong> for these HTTP calls.</p>

                     <h3 className="text-lg font-semibold text-purple-100 mt-4">Step 4: Manage User Authentication and Data</h3>
                     <p>The app will need its own Login/Signup screens to authenticate against the Base44 auth system. Every subsequent API call must include the returned JWT in the `Authorization` header. To save/retrieve user data (like favorites), make authenticated API calls to the Base44 database endpoints for the `FavoriteChannel` and `WatchProgress` entities.</p>
                </Section>

                <Section title="3. Project File Structure & Explanation">
                    <h3 className="text-xl font-semibold text-purple-100 mt-4 mb-2">Backend Files (Serverless Functions)</h3>
                    <p className="mb-2">The Android developer will make HTTP requests to these functions.</p>
                    <ul className="list-disc list-inside space-y-2 pl-4">
                        <li>`functions/xtreamProxy.js`: **THE MOST IMPORTANT FUNCTION.** The secure gateway to the IPTV provider.</li>
                        <li>`functions/getTmdbDetails.js`: Fetches rich metadata (posters, cast) from TMDB.</li>
                        <li>`functions/geminiProxy.js`: The secure proxy to the Google Gemini AI for Smart Search.</li>
                        <li>`functions/fetchEPG.js`: Backend job for fetching EPG data.</li>
                    </ul>

                     <h3 className="text-xl font-semibold text-purple-100 mt-4 mb-2">Database Schema (Entities)</h3>
                     <p className="mb-2">Defines the data structure. The Android app will read/write to these tables via authenticated API calls.</p>
                     <ul className="list-disc list-inside space-y-2 pl-4">
                         <li>`entities/FavoriteChannel.json`: Stores a user's favorite channels.</li>
                         <li>`entities/WatchProgress.json`: Stores viewing progress for movies and episodes.</li>
                     </ul>

                    <h3 className="text-xl font-semibold text-purple-100 mt-4 mb-2">Frontend Files (React) - Reference for UI Replication</h3>
                     <ul className="list-disc list-inside space-y-2 pl-4">
                        <li>`pages/Dashboard.js`, `AddAccount.js`, `MainMenu.js`</li>
                        <li>`pages/LiveTVMain.js`, `LiveCategories.js`, `Channels.js`</li>
                        <li>`pages/MovieCategories.js`, `Movies.js`, `MovieInfo.js`</li>
                        <li>`pages/SeriesCategories.js`, `SeriesGrid.js`, `SeriesDetails.js`</li>
                        <li>`pages/Player.js`, `Guide.js`, `Favorites.js`</li>
                        <li>`pages/GlobalSearch.js`, `AiSearch.js`</li>
                     </ul>
                </Section>
            </div>
        </div>
    );
}