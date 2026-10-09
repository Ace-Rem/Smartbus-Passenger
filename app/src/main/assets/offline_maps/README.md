Place a licensed `smartbus.mbtiles` file in this directory before building the passenger APK.

The app copies that archive to osmdroid's app-specific archive directory before the MapView is
created. Do not put tiles downloaded from `tile.openstreetmap.org` here; the public OSM raster
server does not permit bulk download/offline packages. The online priority is Stadia Maps, then
OSM for a bounded fallback request. See `tools/build_offline_mbtiles.py` and `docs/OFFLINE_MAPS.md`.
