/**
 * pages.config.js - Page routing configuration
 * 
 * This file is AUTO-GENERATED. Do not add imports or modify PAGES manually.
 * Pages are auto-registered when you create files in the ./pages/ folder.
 * 
 * THE ONLY EDITABLE VALUE: mainPage
 * This controls which page is the landing page (shown when users visit the app).
 * 
 * Example file structure:
 * 
 *   import HomePage from './pages/HomePage';
 *   import Dashboard from './pages/Dashboard';
 *   import Settings from './pages/Settings';
 *   
 *   export const PAGES = {
 *       "HomePage": HomePage,
 *       "Dashboard": Dashboard,
 *       "Settings": Settings,
 *   }
 *   
 *   export const pagesConfig = {
 *       mainPage: "HomePage",
 *       Pages: PAGES,
 *   };
 * 
 * Example with Layout (wraps all pages):
 *
 *   import Home from './pages/Home';
 *   import Settings from './pages/Settings';
 *   import __Layout from './Layout.jsx';
 *
 *   export const PAGES = {
 *       "Home": Home,
 *       "Settings": Settings,
 *   }
 *
 *   export const pagesConfig = {
 *       mainPage: "Home",
 *       Pages: PAGES,
 *       Layout: __Layout,
 *   };
 *
 * To change the main page from HomePage to Dashboard, use find_replace:
 *   Old: mainPage: "HomePage",
 *   New: mainPage: "Dashboard",
 *
 * The mainPage value must match a key in the PAGES object exactly.
 */
import AddAccount from './pages/AddAccount';
import AiSearch from './pages/AiSearch';
import CapacitorSetup from './pages/CapacitorSetup';
import CastSearch from './pages/CastSearch';
import Channels from './pages/Channels';
import Dashboard from './pages/Dashboard';
import DeveloperGuide from './pages/DeveloperGuide';
import Favorites from './pages/Favorites';
import GlobalSearch from './pages/GlobalSearch';
import Guide from './pages/Guide';
import Home from './pages/Home';
import LiveCategories from './pages/LiveCategories';
import LiveTVMain from './pages/LiveTVMain';
import MainMenu from './pages/MainMenu';
import MovieCategories from './pages/MovieCategories';
import MovieInfo from './pages/MovieInfo';
import Movies from './pages/Movies';
import Player from './pages/Player';
import SeriesCategories from './pages/SeriesCategories';
import SeriesDetails from './pages/SeriesDetails';
import SeriesGrid from './pages/SeriesGrid';
import Welcome from './pages/Welcome';
import __Layout from './Layout.jsx';


export const PAGES = {
    "AddAccount": AddAccount,
    "AiSearch": AiSearch,
    "CapacitorSetup": CapacitorSetup,
    "CastSearch": CastSearch,
    "Channels": Channels,
    "Dashboard": Dashboard,
    "DeveloperGuide": DeveloperGuide,
    "Favorites": Favorites,
    "GlobalSearch": GlobalSearch,
    "Guide": Guide,
    "Home": Home,
    "LiveCategories": LiveCategories,
    "LiveTVMain": LiveTVMain,
    "MainMenu": MainMenu,
    "MovieCategories": MovieCategories,
    "MovieInfo": MovieInfo,
    "Movies": Movies,
    "Player": Player,
    "SeriesCategories": SeriesCategories,
    "SeriesDetails": SeriesDetails,
    "SeriesGrid": SeriesGrid,
    "Welcome": Welcome,
}

export const pagesConfig = {
    mainPage: "Dashboard",
    Pages: PAGES,
    Layout: __Layout,
};