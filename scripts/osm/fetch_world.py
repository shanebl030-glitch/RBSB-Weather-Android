#!/usr/bin/env python3
# Fetches OpenStreetMap (Nominatim) reverse-geocoding replies for places around the world, exactly
# as RBSB-Weather asks (zoom 17, address details, rounded to 3 decimals), one request every 1.5 s
# per Nominatim's usage policy. Prints each reply on one line for the test fixtures.
# Created By Shane BlackbuRn
import json, time, urllib.request
PLACES = [
  ("nz_east_tamaki", -36.946, 174.896), ("nz_waiuku", -37.249, 174.731), ("nz_clarks_beach", -37.133, 174.700),
  ("nz_ponsonby", -36.858, 174.746), ("nz_wellington_cbd", -41.287, 174.776),
  ("uk_shoreditch", 51.526, -0.079), ("uk_cotswolds_village", 51.833, -1.833),
  ("fr_paris_11", 48.859, 2.380), ("fr_gordes", 43.912, 5.200),
  ("de_kreuzberg", 52.499, 13.403), ("nl_jordaan", 52.376, 4.880),
  ("us_williamsburg", 40.708, -73.957), ("us_marfa", 30.309, -104.021), ("us_rural_kansas", 38.500, -98.500),
  ("ca_annex_toronto", 43.670, -79.405), ("mx_coyoacan", 19.350, -99.162), ("br_copacabana", -22.971, -43.182),
  ("jp_shibuya", 35.660, 139.700), ("cn_chaoyang", 39.922, 116.444), ("in_bandra", 19.060, 72.830),
  ("tr_kadikoy", 40.990, 29.030), ("ae_dubai_marina", 25.081, 55.140), ("ke_westlands", -1.268, 36.811),
  ("za_sea_point", -33.915, 18.387), ("au_bondi", -33.892, 151.277), ("is_reykjavik", 64.147, -21.943),
  ("es_gracia_barcelona", 41.403, 2.157), ("it_trastevere", 41.889, 12.469), ("kr_gangnam", 37.498, 127.028),
  ("sea_tasman", -38.000, 165.000),
]
for name, lat, lon in PLACES:
    url = f"https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat={lat:.3f}&lon={lon:.3f}&zoom=17&addressdetails=1"
    req = urllib.request.Request(url, headers={"User-Agent": "RBSB-Weather-Android test fixtures (github.com/shanebl030-glitch/RBSB-Weather-Android)",
                                               "Accept-Language": "en"})
    try:
        body = json.loads(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))
        keep = {k: body[k] for k in ("address", "error", "addresstype", "name") if k in body}
        print("OSMFIX", name, lat, lon, json.dumps(keep, ensure_ascii=False, separators=(",", ":")), flush=True)
    except Exception as e:
        print("OSMFIX", name, lat, lon, json.dumps({"fetch_error": str(e)}), flush=True)
    time.sleep(1.5)
