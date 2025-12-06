import Dashboard from './pages/Dashboard';
import Channels from './pages/Channels';
import MainMenu from './pages/MainMenu';
import Movies from './pages/Movies';
import SeriesDetails from './pages/SeriesDetails';
import AddAccount from './pages/AddAccount';
import LiveCategories from './pages/LiveCategories';
import MovieCategories from './pages/MovieCategories';
import SeriesCategories from './pages/SeriesCategories';
import SeriesGrid from './pages/SeriesGrid';
import Guide from './pages/Guide';
import MovieInfo from './pages/MovieInfo';
import Player from './pages/Player';
import Favorites from './pages/Favorites';
import LiveTVMain from './pages/LiveTVMain';
import GlobalSearch from './pages/GlobalSearch';
import AiSearch from './pages/AiSearch';
import DeveloperGuide from './pages/DeveloperGuide';
import Welcome from './pages/Welcome';
import CastSearch from './pages/CastSearch';
import CapacitorSetup from './pages/CapacitorSetup';
import __Layout from './Layout.jsx';


export const PAGES = {
    "Dashboard": Dashboard,
    "Channels": Channels,
    "MainMenu": MainMenu,
    "Movies": Movies,
    "SeriesDetails": SeriesDetails,
    "AddAccount": AddAccount,
    "LiveCategories": LiveCategories,
    "MovieCategories": MovieCategories,
    "SeriesCategories": SeriesCategories,
    "SeriesGrid": SeriesGrid,
    "Guide": Guide,
    "MovieInfo": MovieInfo,
    "Player": Player,
    "Favorites": Favorites,
    "LiveTVMain": LiveTVMain,
    "GlobalSearch": GlobalSearch,
    "AiSearch": AiSearch,
    "DeveloperGuide": DeveloperGuide,
    "Welcome": Welcome,
    "CastSearch": CastSearch,
    "CapacitorSetup": CapacitorSetup,
}

export const pagesConfig = {
    mainPage: "Dashboard",
    Pages: PAGES,
    Layout: __Layout,
};