import re
from pathlib import Path
from typing import Dict, List, Tuple, Optional

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

# ================= CONFIGURATION =================
PACKAGE_NAME = "com.slimepop.asmr"
JSON_KEY_FILE = "service-account.json"
PURCHASE_OPTION_ID = "buy"
CREATE_FULL_CATALOG = True
CREATE_BUNDLES = True
BUNDLE_COUNT = 20
REQUEST_BATCH_SIZE = 100

# Price tiers (USD)
PRICE_REMOVE_ADS_MICROS = 4490000  # $4.49
PRICE_SKIN_MICROS = 1490000       # $1.49
PRICE_SOUND_MICROS = 1990000      # $1.99
PRICE_BUNDLE_MICROS = 3490000     # $3.49
PRICE_DEFAULT_MICROS = 990000     # Fallback

# Update pricing for existing products (not just new).
UPDATE_EXISTING_PRICES = True
# =================================================

ROOT = Path(__file__).resolve().parent
SKIN_CATALOG = ROOT / "app" / "src" / "main" / "java" / "com" / "slimepop" / "asmr" / "SkinCatalog.kt"
SOUND_CATALOG = ROOT / "app" / "src" / "main" / "java" / "com" / "slimepop" / "asmr" / "SoundCatalog.kt"
SCOPE = ["https://www.googleapis.com/auth/androidpublisher"]
API_ROOT = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}"
LATENCY_TOLERANT = "PRODUCT_UPDATE_LATENCY_TOLERANCE_LATENCY_TOLERANT"


def get_session() -> AuthorizedSession:
    creds = service_account.Credentials.from_service_account_file(JSON_KEY_FILE, scopes=SCOPE)
    return AuthorizedSession(creds)


def chunks(items: List, size: int):
    for i in range(0, len(items), size):
        yield items[i : i + size]


def micros_to_money(micros: int, currency_code: str) -> Dict:
    units = micros // 1_000_000
    nanos = (micros % 1_000_000) * 1000
    return {"currencyCode": currency_code, "units": str(units), "nanos": nanos}


def load_premium_products_from_catalogs() -> List[Tuple[str, str, str]]:
    products: List[Tuple[str, str, str]] = []

    if SKIN_CATALOG.exists():
        for line in SKIN_CATALOG.read_text(encoding="utf-8").splitlines():
            if "SlimeSkin(" not in line or "isIAP = true" not in line:
                continue
            match = re.search(r'SlimeSkin\("([^"]+)",\s*"([^"]+)"', line)
            if not match:
                continue
            sku_id, name = match.group(1), match.group(2)
            products.append((sku_id, f"{name} Skin", f"Unlock the premium {name} slime skin."))

    if SOUND_CATALOG.exists():
        for line in SOUND_CATALOG.read_text(encoding="utf-8").splitlines():
            if "SlimeSound(" not in line or "isIAP = true" not in line:
                continue
            match = re.search(r'SlimeSound\("([^"]+)",\s*"([^"]+)"', line)
            if not match:
                continue
            sku_id, name = match.group(1), match.group(2)
            products.append((sku_id, name, f"Unlock the premium {name} ASMR sound."))

    return products


def load_bundle_products() -> List[Tuple[str, str, str]]:
    bundles: List[Tuple[str, str, str]] = []
    for i in range(1, BUNDLE_COUNT + 1):
        sku_id = f"bundle_{i:02d}"
        title = f"Relax Pack {i:02d}"
        description = "Bundle of 3 premium items."
        bundles.append((sku_id, title, description))
    return bundles


def convert_region_prices(session: AuthorizedSession, price_micros: int) -> Tuple[Dict, List[Dict], Dict]:
    url = f"{API_ROOT}/pricing:convertRegionPrices"
    body = {"price": micros_to_money(price_micros, "USD")}

    resp = session.post(url, json=body, timeout=60)
    if not resp.ok:
        raise RuntimeError(f"convertRegionPrices failed ({resp.status_code}): {resp.text}")

    data = resp.json()
    regions_version = data.get("regionVersion")
    converted = data.get("convertedRegionPrices", {})
    other_regions = data.get("convertedOtherRegionsPrice", {})

    if not regions_version:
        raise RuntimeError("convertRegionPrices response missing regionVersion")

    regional_configs: List[Dict] = []
    for item in converted.values():
        region_code = item.get("regionCode")
        region_price = item.get("price")
        if not region_code or not region_price:
            continue
        regional_configs.append(
            {
                "regionCode": region_code,
                "price": region_price,
                "availability": "AVAILABLE",
            }
        )

    if not regional_configs:
        raise RuntimeError("convertRegionPrices returned no regional pricing configs")

    new_regions_config = {
        "usdPrice": other_regions.get("usdPrice"),
        "eurPrice": other_regions.get("eurPrice"),
        "availability": "AVAILABLE",
    }
    if not new_regions_config["usdPrice"] or not new_regions_config["eurPrice"]:
        new_regions_config = {}

    return regions_version, regional_configs, new_regions_config


def fetch_existing_product(session: AuthorizedSession, product_id: str) -> Dict:
    url = f"{API_ROOT}/oneTimeProducts/{product_id}"
    resp = session.get(url, timeout=60)
    if resp.status_code == 404:
        return {}
    if not resp.ok:
        raise RuntimeError(f"fetch existing product failed ({resp.status_code}) for {product_id}: {resp.text}")
    return resp.json()


def split_existing_and_new_products(
    session: AuthorizedSession, products: List[Tuple[str, str, str]]
) -> Tuple[List[Dict], List[Tuple[str, str, str]]]:
    existing: List[Dict] = []
    new: List[Tuple[str, str, str]] = []

    for sku_id, title, description in products:
        existing_product = fetch_existing_product(session, sku_id)
        if existing_product:
            purchase_options = existing_product.get("purchaseOptions") or []
            option_ids = [opt.get("purchaseOptionId") for opt in purchase_options if opt.get("purchaseOptionId")]
            if not option_ids:
                option_ids = [PURCHASE_OPTION_ID]
            existing.append(
                {
                    "sku_id": sku_id,
                    "title": title,
                    "description": description,
                    "purchase_option_ids": option_ids,
                }
            )
        else:
            new.append((sku_id, title, description))

    return existing, new


def price_micros_for_product(product_id: str) -> int:
    pid = product_id.lower().strip()
    if pid == "remove_ads":
        return PRICE_REMOVE_ADS_MICROS
    if pid.startswith("skin_"):
        return PRICE_SKIN_MICROS
    if pid.startswith("sound_"):
        return PRICE_SOUND_MICROS
    if pid.startswith("bundle_"):
        return PRICE_BUNDLE_MICROS
    return PRICE_DEFAULT_MICROS


def build_upsert_requests(
    products: List[Dict],
    price_configs: Dict[int, Tuple[Dict, List[Dict], Dict]],
    allow_missing: bool,
    include_pricing: bool,
) -> List[Dict]:
    requests: List[Dict] = []
    for item in products:
        sku_id = item["sku_id"]
        title = item["title"]
        description = item["description"]
        purchase_option_ids: Optional[List[str]] = item.get("purchase_option_ids")
        if not purchase_option_ids:
            purchase_option_ids = [PURCHASE_OPTION_ID]
        price_micros = price_micros_for_product(sku_id)
        regions_version, regional_configs, new_regions_config = price_configs[price_micros]
        one_time_product = {
            "packageName": PACKAGE_NAME,
            "productId": sku_id.lower().strip(),
            "listings": [
                {
                    "languageCode": "en-US",
                    "title": title[:55],
                    "description": description[:200],
                }
            ],
        }
        if include_pricing:
            options = []
            for option_id in purchase_option_ids:
                option = {
                    "purchaseOptionId": option_id,
                    "buyOption": {
                        "legacyCompatible": True,
                        "multiQuantityEnabled": False,
                    },
                    "regionalPricingAndAvailabilityConfigs": regional_configs,
                }
                if new_regions_config:
                    option["newRegionsConfig"] = new_regions_config
                options.append(option)
            one_time_product["purchaseOptions"] = options

        requests.append(
            {
                "oneTimeProduct": one_time_product,
                "updateMask": "listings,purchaseOptions" if include_pricing else "listings",
                "regionsVersion": regions_version,
                "allowMissing": allow_missing,
                "latencyTolerance": LATENCY_TOLERANT,
            }
        )
    return requests


def batch_upsert_products(session: AuthorizedSession, requests: List[Dict]) -> None:
    url = f"{API_ROOT}/oneTimeProducts:batchUpdate"
    total = len(requests)
    done = 0
    for group in chunks(requests, REQUEST_BATCH_SIZE):
        body = {"requests": group}
        resp = session.post(url, json=body, timeout=120)
        if not resp.ok:
            raise RuntimeError(f"oneTimeProducts:batchUpdate failed ({resp.status_code}): {resp.text}")
        done += len(group)
        print(f"Upserted {done}/{total} products")


def batch_activate_purchase_options(session: AuthorizedSession, product_ids: List[str]) -> None:
    url = f"{API_ROOT}/oneTimeProducts/-/purchaseOptions:batchUpdateStates"
    total = len(product_ids)
    done = 0
    for group in chunks(product_ids, REQUEST_BATCH_SIZE):
        requests = []
        for product_id in group:
            requests.append(
                {
                    "activatePurchaseOptionRequest": {
                        "packageName": PACKAGE_NAME,
                        "productId": product_id,
                        "purchaseOptionId": PURCHASE_OPTION_ID,
                        "latencyTolerance": LATENCY_TOLERANT,
                    }
                }
            )
        resp = session.post(url, json={"requests": requests}, timeout=120)
        if not resp.ok:
            raise RuntimeError(
                f"purchaseOptions:batchUpdateStates failed ({resp.status_code}): {resp.text}"
            )
        done += len(group)
        print(f"Activated purchase options for {done}/{total} products")


def main():
    session = get_session()

    products: List[Tuple[str, str, str]] = [
        ("remove_ads", "Remove Ads", "Permanently remove ads from Slime Pop."),
    ]

    if CREATE_FULL_CATALOG:
        products.extend(load_premium_products_from_catalogs())

    if CREATE_BUNDLES:
        products.extend(load_bundle_products())

    seen = set()
    deduped = []
    for sku_id, title, description in products:
        sku = sku_id.lower().strip()
        if sku in seen:
            continue
        seen.add(sku)
        deduped.append((sku, title, description))
    products = deduped

    print(f"Preparing {len(products)} products for package {PACKAGE_NAME}")
    existing_products, new_products = split_existing_and_new_products(session, products)
    print(f"Found {len(existing_products)} existing products, {len(new_products)} new products")
    price_points = sorted({price_micros_for_product(p[0]) for p in products})
    price_configs: Dict[int, Tuple[Dict, List[Dict], Dict]] = {}
    for micros in price_points:
        price_configs[micros] = convert_region_prices(session, micros)

    existing_requests = build_upsert_requests(
        existing_products,
        price_configs,
        allow_missing=False,
        include_pricing=UPDATE_EXISTING_PRICES,
    )
    if existing_requests:
        batch_upsert_products(session, existing_requests)

    if new_products:
        new_items = [
            {"sku_id": sku_id, "title": title, "description": description, "purchase_option_ids": [PURCHASE_OPTION_ID]}
            for (sku_id, title, description) in new_products
        ]
        create_requests = build_upsert_requests(
            new_items,
            price_configs,
            allow_missing=True,
            include_pricing=True,
        )
        batch_upsert_products(session, create_requests)
        batch_activate_purchase_options(session, [p[0] for p in new_products])

    print("Done. Existing listings updated; new products created and activated.")


if __name__ == "__main__":
    main()
