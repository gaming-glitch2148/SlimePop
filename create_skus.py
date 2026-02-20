import re
from pathlib import Path
from typing import Dict, List, Tuple

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

# ================= CONFIGURATION =================
PACKAGE_NAME = "com.slimepop.asmr"
JSON_KEY_FILE = "service-account.json"
PRICE_MICROS = 990000  # $0.99
PURCHASE_OPTION_ID = "buy"
CREATE_FULL_CATALOG = True
REQUEST_BATCH_SIZE = 100
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


def convert_region_prices(session: AuthorizedSession) -> Tuple[Dict, List[Dict], Dict]:
    url = f"{API_ROOT}/pricing:convertRegionPrices"
    body = {"price": micros_to_money(PRICE_MICROS, "USD")}

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
) -> Tuple[List[Tuple[str, str, str]], List[Tuple[str, str, str]]]:
    existing: List[Tuple[str, str, str]] = []
    new: List[Tuple[str, str, str]] = []

    for sku_id, title, description in products:
        existing_product = fetch_existing_product(session, sku_id)
        if existing_product:
            existing.append((sku_id, title, description))
        else:
            new.append((sku_id, title, description))

    return existing, new


def build_batch_update_requests(
    products: List[Tuple[str, str, str]],
    regions_version: Dict,
    regional_configs: List[Dict],
    new_regions_config: Dict,
) -> List[Dict]:
    requests: List[Dict] = []
    for sku_id, title, description in products:
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
            "purchaseOptions": [
                {
                    "purchaseOptionId": PURCHASE_OPTION_ID,
                    "buyOption": {
                        "legacyCompatible": True,
                        "multiQuantityEnabled": False,
                    },
                    "regionalPricingAndAvailabilityConfigs": regional_configs,
                }
            ],
        }
        if new_regions_config:
            one_time_product["purchaseOptions"][0]["newRegionsConfig"] = new_regions_config

        requests.append(
            {
                "oneTimeProduct": one_time_product,
                "updateMask": "listings,purchaseOptions",
                "regionsVersion": regions_version,
                "allowMissing": True,
                "latencyTolerance": LATENCY_TOLERANT,
            }
        )
    return requests


def build_listing_only_update_requests(
    products: List[Tuple[str, str, str]], regions_version: Dict
) -> List[Dict]:
    requests: List[Dict] = []
    for sku_id, title, description in products:
        requests.append(
            {
                "oneTimeProduct": {
                    "packageName": PACKAGE_NAME,
                    "productId": sku_id.lower().strip(),
                    "listings": [
                        {
                            "languageCode": "en-US",
                            "title": title[:55],
                            "description": description[:200],
                        }
                    ],
                },
                "updateMask": "listings",
                "regionsVersion": regions_version,
                "allowMissing": False,
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
    regions_version, regional_configs, new_regions_config = convert_region_prices(session)

    existing_requests = build_listing_only_update_requests(existing_products, regions_version)
    if existing_requests:
        batch_upsert_products(session, existing_requests)

    if new_products:
        create_requests = build_batch_update_requests(
            new_products, regions_version, regional_configs, new_regions_config
        )
        batch_upsert_products(session, create_requests)
        batch_activate_purchase_options(session, [p[0] for p in new_products])

    print("Done. Existing listings updated; new products created and activated.")


if __name__ == "__main__":
    main()
