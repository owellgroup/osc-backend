# OSC Store API — Postman guide

Base URL when the app is running on this machine:

```text
http://localhost:8286
```

The server listens on port **8286**. Every path below is relative to that host. Send JSON bodies with header `Content-Type: application/json`.

## Postman setup

1. Create an environment with a variable `baseUrl` = `http://localhost:8286`.
2. Create a collection and set the collection auth to **Bearer Token**, token `{{token}}`.
3. On the login request only, set Auth to **No Auth**. Public catalog, image, and quote-submit requests can also use **No Auth**.
4. After login, the Tests script below stores the token for every later request:

```javascript
const body = pm.response.json();
pm.environment.set("token", body.token);
pm.environment.set("adminId", body.admin.id);
```

The token is a JWT. It expires **12 hours** after login. Send it as:

```http
Authorization: Bearer {{token}}
```

There is no refresh endpoint. Log in again when requests start returning 401.

### Who can call what

| Access | Routes |
| --- | --- |
| Anyone | `POST /api/auth/login`, `GET /api/catalog/**`, `GET /api/files/images/**`, `POST /api/quotes` |
| `ADMIN` or `SUPER_ADMIN` | `GET /api/auth/me`, everything under `/api/admin/**` except admin-user management |
| `SUPER_ADMIN` only | `/api/admin/admins/**` |

A missing or invalid token on a protected route returns **401** with an empty body. A valid token with the wrong role returns **403** with an empty body. Any other path returns **403**.

The first startup creates a super admin from `application.properties`:

| Field | Default |
| --- | --- |
| Email | `joel@wayaspot.com` |
| Password | `Admin@123` |
| Role | `SUPER_ADMIN` |

New admins created through the API are always role `ADMIN`. The super admin account cannot be edited or deleted.

### Suggested order

1. `POST /api/auth/login`
2. `GET /api/catalog/products` and note a product `id`
3. `POST /api/quotes` with that product
4. `GET /api/admin/quotes` and open the new quote
5. `PUT /api/admin/quotes/{id}` to set prices
6. `POST /api/admin/quotes/{id}/process`
7. `GET /api/admin/quotes/{id}/pdf`

Mail host, username, and password are empty in `.env`, so process and resend still succeed, but `emailSent` will be `false` and `emailError` will explain the SMTP failure. MinIO’s Docker hostname does not resolve from this machine, so image upload and quote file upload fail until `MINIO_ENDPOINT` is reachable.

## Shared types

JSON field names are camelCase. Enums are uppercase strings. Money fields are JSON numbers with up to 2 decimal places (`BigDecimal`). `Long` ids are JSON numbers. Timestamps (`Instant`) are ISO-8601, for example `2026-11-07T16:17:12.106Z`. Dates (`LocalDate`) are `yyyy-MM-dd`.

### Enums

| Name | Values |
| --- | --- |
| `ProductType` | `PRODUCT`, `SOLUTION` |
| `CustomerType` | `COMPANY`, `INDIVIDUAL` |
| `QuoteStatus` | `NEW`, `PROCESSED` |
| `AdminRole` | `SUPER_ADMIN`, `ADMIN` |
| Banner `backgroundStyle` | `solid`, `linear-right`, `linear-left`, `linear-down`, `linear-up`, `diagonal`, `radial`, `glow`, `wash` |

### PageResponse

Used by product and quote lists. `page` is **zero-based**. `size` is clamped to 1–100.

| Field | Type |
| --- | --- |
| `items` | array of the resource |
| `total` | number (long) |
| `page` | number (int) |
| `size` | number (int) |
| `totalPages` | number (int) |

### Error body

Validation and business errors return JSON:

```json
{
  "status": 400,
  "message": "Name is required",
  "fields": {
    "name": "Name is required"
  }
}
```

`fields` is present only for bean-validation errors. Other statuses:

| Status | When |
| --- | --- |
| 400 | Validation or a business rule failed |
| 401 | Login failed (`{"status":401,"message":"Incorrect email or password"}`) |
| 403 | Authenticated, but not allowed. Super-admin protection also returns JSON: `The super admin account cannot be edited` |
| 404 | Resource not found, message like `Product not found` |
| 409 | Duplicate name, SKU, email, or a category/brand still used by products |
| 413 | A file is over 15 MB. Whole request limit is 70 MB |
| 500 | Unexpected server error |

## Auth

### POST `/api/auth/login`

Public. Body `LoginRequest`:

| Field | Type | Rules |
| --- | --- | --- |
| `email` | string | Required |
| `password` | string | Required |

```json
{
  "email": "joel@wayaspot.com",
  "password": "Admin@123"
}
```

**200** `LoginResponse`:

| Field | Type |
| --- | --- |
| `token` | string (JWT) |
| `admin` | `AdminDto` |

`AdminDto`:

| Field | Type |
| --- | --- |
| `id` | number |
| `name` | string |
| `email` | string |
| `role` | `AdminRole` |
| `protectedAccount` | boolean. `true` for the super admin |
| `createdAt` | timestamp |

### GET `/api/auth/me`

Admin or super admin. No body.

**200** `AdminDto` for the token’s user.

## Public catalog

No prices are returned. Inactive products are hidden. Results are ordered featured first, then newest. Default page size is **24**.

### GET `/api/catalog/products`

Query parameters, all optional:

| Name | Type | Meaning |
| --- | --- | --- |
| `q` | string | Case-insensitive match on name, SKU, or summary |
| `type` | `ProductType` | `PRODUCT` or `SOLUTION` |
| `categoryId` | number | |
| `brandId` | number | |
| `featured` | boolean | `true` or `false` |
| `page` | number | Default `0` |
| `size` | number | Default `24`, max `100` |

Example: `GET {{baseUrl}}/api/catalog/products?q=switch&type=PRODUCT&featured=true&page=0&size=24`

**200** `PageResponse<ProductDto>`.

`ProductDto`:

| Field | Type |
| --- | --- |
| `id` | number |
| `name` | string |
| `sku` | string or null |
| `type` | `ProductType` |
| `summary` | string or null |
| `description` | string or null |
| `categoryId` | number or null |
| `categoryName` | string or null |
| `brandId` | number or null |
| `brandName` | string or null |
| `images` | array of string. Each value is a path such as `/api/files/images/{file}` |
| `specifications` | array of `SpecDto` |
| `options` | array of `OptionDto` |
| `featured` | boolean |
| `active` | boolean. Always `true` on this public list |

`SpecDto`: `label` string, `value` string.

`OptionDto`: `id` number, `name` string, `values` array of string.

### GET `/api/catalog/products/{id}`

**200** `ProductDto`. **404** if the id is missing or the product is inactive.

### GET `/api/catalog/categories`

**200** array of `NamedDto`: `id` number, `name` string, `description` string or null.

### GET `/api/catalog/brands`

**200** array of `NamedDto`. Brand descriptions are usually null.

### GET `/api/catalog/banners`

**200** array of active `BannerDto`, ordered by `position`, then `id`. Inactive banners are omitted. See the banner section for the field list.

### GET `/api/files/images/{name}`

Public. `{name}` is the file name only, for example `3f2a....jpg`, not a path. Allowed characters are letters, digits, `.`, `_`, and `-`.

**200** image bytes. `Content-Type` is the stored image type. Cache header is one year, public, immutable.

**404** if the name is unsafe or the object is missing. This call needs a reachable MinIO bucket.

## Submit a quote

### POST `/api/quotes`

Public. **201**. This is `multipart/form-data`, not a raw JSON body.

In Postman, Body → form-data:

| Key | Type | Required | Value |
| --- | --- | --- | --- |
| `quote` | Text | yes | The JSON object below. After adding the row, open the key’s menu and set Content-Type to `application/json` |
| `files` | File | no | Repeat this key once per file, up to **5** files |

Each file may be up to 15 MB. Allowed extensions: `pdf`, `doc`, `docx`, `xls`, `xlsx`, `csv`, `txt`, `ppt`, `pptx`, `zip`, `png`, `jpg`, `jpeg`.

`quote` is `QuoteSubmitRequest`:

| Field | Type | Rules |
| --- | --- | --- |
| `customerType` | `CustomerType` | Required |
| `companyName` | string | Max 255. Required when type is `COMPANY` |
| `registrationNumber` | string | Max 100. Optional |
| `vatNumber` | string | Max 100. Optional |
| `companyEmail` | string | Valid email, max is the email constraint. Required when type is `COMPANY` |
| `companyPhone` | string | Max 60 |
| `companyAddress` | string | Max 500. Required when type is `COMPANY` |
| `companyLocation` | string | Max 255 |
| `contactName` | string | Required, max 255 |
| `contactEmail` | string | Required, valid email |
| `contactPhone` | string | Max 60 |
| `contactJobTitle` | string | Max 255. Stored only for `COMPANY` |
| `deliveryAddress` | string | Required, max 500 |
| `deliveryLocation` | string | Max 255 |
| `questions` | string | Max 5000 |
| `items` | array of `Item` | Required, at least one |

`Item`:

| Field | Type | Rules |
| --- | --- | --- |
| `productId` | number | Required. Product must exist and be active |
| `quantity` | number | Integer from 1 to 100000 |
| `options` | object | Map of option **name** to chosen **value**. Omitted keys are skipped. The value must be one of that option’s values on the product |

Identical product and option combinations in one request are merged into one line and their quantities are added.

Company example:

```json
{
  "customerType": "COMPANY",
  "companyName": "Acme Mining",
  "registrationNumber": "2021/123456/07",
  "vatNumber": "4123456789",
  "companyEmail": "procurement@acme.example",
  "companyPhone": "+264 61 000 000",
  "companyAddress": "12 Independence Avenue, Windhoek",
  "companyLocation": "Windhoek",
  "contactName": "Aina Shikongo",
  "contactEmail": "aina@acme.example",
  "contactPhone": "+264 81 000 0000",
  "contactJobTitle": "Buyer",
  "deliveryAddress": "12 Independence Avenue, Windhoek",
  "deliveryLocation": "Windhoek",
  "questions": "Please include installation.",
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "options": {
        "Port count": "24 ports",
        "Warranty term": "3 years"
      }
    }
  ]
}
```

Individual example: set `customerType` to `INDIVIDUAL` and omit the company fields. `contactJobTitle` is ignored.

**201** `QuoteSubmitResponse`:

| Field | Type |
| --- | --- |
| `reference` | string, shape `OSC-Q-{year}-{id padded to 5}`, for example `OSC-Q-2026-00001` |
| `email` | string. Address the acknowledgement is sent to |

The quote is stored as status `NEW`. Currency is `NAD`. VAT percent starts at `15.00`. Line prices are null until an admin sets them.

## Admin quotes

All routes need `ADMIN` or `SUPER_ADMIN`. List page size default is **20**, max **100**. Newest quotes come first.

### GET `/api/admin/quotes`

| Query | Type | Meaning |
| --- | --- | --- |
| `status` | `QuoteStatus` | `NEW` or `PROCESSED` |
| `q` | string | Reference, company name, contact name, or contact email |
| `page` | number | Default `0` |
| `size` | number | Default `20` |

**200** `PageResponse<QuoteSummaryDto>`.

`QuoteSummaryDto`:

| Field | Type |
| --- | --- |
| `id` | number |
| `reference` | string |
| `customerType` | `CustomerType` |
| `customerName` | string |
| `email` | string |
| `status` | `QuoteStatus` |
| `productCount` | number |
| `solutionCount` | number |
| `totalQuantity` | number |
| `total` | number. `0` until priced |
| `createdAt` | timestamp |
| `processedAt` | timestamp or null |

### GET `/api/admin/quotes/stats`

**200** `QuoteStatsDto`: `total` number, `newCount` number, `processedCount` number.

### GET `/api/admin/quotes/{id}`

**200** `QuoteDetailDto`. **404** if the quote does not exist.

| Field | Type |
| --- | --- |
| `id` | number |
| `reference` | string |
| `customerType` | `CustomerType` |
| `status` | `QuoteStatus` |
| `companyName`, `registrationNumber`, `vatNumber`, `companyEmail`, `companyPhone`, `companyAddress`, `companyLocation` | string or null |
| `contactName`, `contactEmail` | string |
| `contactPhone`, `contactJobTitle` | string or null |
| `deliveryAddress` | string |
| `deliveryLocation`, `questions` | string or null |
| `currency` | string. Always `NAD` |
| `productsSubtotal`, `solutionsSubtotal`, `deliveryFee`, `discount`, `vatPercent`, `vatAmount`, `total` | number |
| `validUntil` | date or null |
| `warranty`, `deliveryTimeline`, `availability`, `sourcing`, `paymentTerms`, `additionalNotes` | string or null |
| `createdAt` | timestamp |
| `processedAt` | timestamp or null |
| `processedBy` | string or null. Admin email |
| `emailSentAt` | timestamp or null |
| `emailError` | string or null |
| `items` | array of `QuoteItemDto` |
| `attachments` | array of `QuoteAttachmentDto` |

`QuoteItemDto`:

| Field | Type |
| --- | --- |
| `id` | number. This is the line id used when pricing, not the product id |
| `productId` | number |
| `name`, `sku`, `brandName` | string or null |
| `type` | `ProductType` |
| `imagePath` | string or null |
| `selectedOptions` | string or null. Example: `Port count: 24 ports, Warranty term: 3 years` |
| `quantity` | number |
| `unitPrice`, `lineTotal` | number or null |
| `availability`, `warranty`, `deliveryTimeline`, `sourcing`, `notes` | string or null |

`QuoteAttachmentDto`: `id` number, `name` string, `contentType` string, `sizeBytes` number.

### PUT `/api/admin/quotes/{id}`

Saves pricing and terms. Does not email the customer and does not change status.

Body `QuoteUpdateRequest`. Every field is optional, but omitted money fields are treated as zero, and omitted text fields clear the stored value. If `vatPercent` is omitted, the quote keeps its current percent, or **15** when it has none.

| Field | Type | Rules |
| --- | --- | --- |
| `deliveryFee` | number | Minimum 0 |
| `discount` | number | Minimum 0. Cannot exceed products + solutions + delivery fee |
| `vatPercent` | number | Minimum 0 |
| `validUntil` | date | `yyyy-MM-dd`, or null |
| `warranty`, `deliveryTimeline`, `availability`, `sourcing`, `paymentTerms` | string | Max 1000 |
| `additionalNotes` | string | |
| `items` | array of `ItemUpdate` | Match lines by quote item `id` |

`ItemUpdate`:

| Field | Type | Rules |
| --- | --- | --- |
| `id` | number | Quote item id from the detail response |
| `unitPrice` | number or null | Minimum 0. Null leaves the line unpriced |
| `availability`, `warranty`, `deliveryTimeline`, `sourcing` | string | Max 255 |
| `notes` | string | Max 1000 |

Totals:

```text
net = productsSubtotal + solutionsSubtotal + deliveryFee - discount
vatAmount = net * vatPercent / 100
total = net + vatAmount
```

```json
{
  "deliveryFee": 500,
  "discount": 0,
  "vatPercent": 15,
  "validUntil": "2026-11-21",
  "warranty": "12 months on hardware",
  "deliveryTimeline": "2 to 3 weeks",
  "availability": "In stock",
  "sourcing": "Local warehouse",
  "paymentTerms": "50% deposit, balance before delivery",
  "additionalNotes": "Installation quoted separately.",
  "items": [
    {
      "id": 1,
      "unitPrice": 12500.5,
      "availability": "In stock",
      "warranty": "3 years",
      "deliveryTimeline": "2 weeks",
      "sourcing": "Local",
      "notes": "Includes stacking cable"
    }
  ]
}
```

**200** `QuoteDetailDto`.

### POST `/api/admin/quotes/{id}/process`

Same body as the save request. Every line must have a `unitPrice`. If `validUntil` is null, the server sets it to **14 days** from today. Status becomes `PROCESSED`, `processedAt` is now, and `processedBy` is the logged-in admin’s email. The server then tries to email the quotation with a PDF attached.

**200** `ProcessResponse`:

| Field | Type |
| --- | --- |
| `quote` | `QuoteDetailDto` |
| `emailSent` | boolean |
| `emailError` | string or null |

**400** if any line has no price, or the discount is too large.

### POST `/api/admin/quotes/{id}/resend`

No body. The quote must already be `PROCESSED`.

**200** `ProcessResponse`. **400** with message `Process the quote first` otherwise.

### GET `/api/admin/quotes/{id}/pdf`

**200** `application/pdf`. `Content-Disposition` is inline, filename `Quotation-{reference}.pdf`. In Postman, use **Send and Download**.

### GET `/api/admin/quotes/{id}/attachments/{attachmentId}`

**200** `application/octet-stream` with the original filename. **404** if that attachment is not on this quote. Needs MinIO.

## Admin products

Admin or super admin. The list includes inactive products. Default page size is **20**.

### GET `/api/admin/products`

Same query parameters as the public product list, except there is no `featured` filter. `q`, `type`, `categoryId`, `brandId`, `page` (default 0), `size` (default 20, max 100).

**200** `PageResponse<ProductDto>`.

### GET `/api/admin/products/{id}`

**200** `ProductDto`, including inactive products. **404** if missing.

### POST `/api/admin/products`

**201** `ProductDto`.

### PUT `/api/admin/products/{id}`

**200** `ProductDto`.

Both write with `ProductRequest`:

| Field | Type | Rules |
| --- | --- | --- |
| `name` | string | Required, max 255 |
| `sku` | string | Optional. Must be unique if set |
| `type` | `ProductType` | Required |
| `summary` | string | Max 500 |
| `description` | string | |
| `categoryId` | number or null | Must exist when set |
| `brandId` | number or null | Must exist when set |
| `images` | array of string | Paths returned by the image upload endpoint |
| `specifications` | array of `SpecDto` | `label` and `value` strings |
| `optionIds` | array of number | Ids from `/api/admin/options` |
| `featured` | boolean | |
| `active` | boolean | `false` hides it from the public catalog and from new quotes |

```json
{
  "name": "Catalyst 9200 Managed Switch",
  "sku": "SW-C9200",
  "type": "PRODUCT",
  "summary": "Enterprise-grade managed access switch.",
  "description": "PoE+ on every port.",
  "categoryId": 1,
  "brandId": 1,
  "images": ["/api/files/images/00000000-0000-0000-0000-000000000000.jpg"],
  "specifications": [
    { "label": "Switching capacity", "value": "176 Gbps" }
  ],
  "optionIds": [1, 2],
  "featured": true,
  "active": true
}
```

**409** if the SKU is already used. **400** if the category, brand, or an option id does not exist.

### DELETE `/api/admin/products/{id}`

**204** empty body.

## Categories, brands, and options

Admin or super admin. Names must be unique within their own list.

`NamedRequest` for categories and brands:

| Field | Type | Rules |
| --- | --- | --- |
| `name` | string | Required, max 150 |
| `description` | string | Max 500 |

**200/201** `NamedDto`: `id`, `name`, `description`.

### Categories

| Method | Path | Status |
| --- | --- | --- |
| GET | `/api/admin/categories` | 200, array of `NamedDto` |
| POST | `/api/admin/categories` | 201 |
| PUT | `/api/admin/categories/{id}` | 200 |
| DELETE | `/api/admin/categories/{id}` | 204 |

Delete returns **409** while any product still uses the category.

### Brands

| Method | Path | Status |
| --- | --- | --- |
| GET | `/api/admin/brands` | 200, array of `NamedDto` |
| POST | `/api/admin/brands` | 201 |
| PUT | `/api/admin/brands/{id}` | 200 |
| DELETE | `/api/admin/brands/{id}` | 204 |

Delete returns **409** while any product still uses the brand.

### Options

`OptionRequest`:

| Field | Type | Rules |
| --- | --- | --- |
| `name` | string | Required |
| `values` | array of string | At least one non-blank value |

```json
{
  "name": "Memory",
  "values": ["8GB", "16GB", "32GB", "64GB"]
}
```

| Method | Path | Status |
| --- | --- | --- |
| GET | `/api/admin/options` | 200, array of `OptionDto` |
| POST | `/api/admin/options` | 201 |
| PUT | `/api/admin/options/{id}` | 200 |
| DELETE | `/api/admin/options/{id}` | 204 |

**200/201** `OptionDto`: `id` number, `name` string, `values` array of string.

## Banners

Admin or super admin. At most **4** banners. Create beyond that returns **409**.

`BannerRequest`:

| Field | Type | Rules |
| --- | --- | --- |
| `title` | string | Required |
| `description` | string | |
| `backgroundColor` | string | `#RRGGBB`. Blank becomes `#D71920`. A 6-digit value without `#` is accepted |
| `backgroundColor2` | string | Same hex rule, or null |
| `backgroundStyle` | string | One of the styles listed above. Blank becomes `solid` |
| `imagePath` | string | Path from the image upload endpoint, or null |
| `ctaText` | string | |
| `linkUrl` | string | |
| `ctaText2` | string | |
| `linkUrl2` | string | |
| `position` | number or null | On create, null means “append” |
| `active` | boolean | Public catalog returns only `true` |

```json
{
  "title": "Campus switching",
  "description": "PoE switches in stock.",
  "backgroundColor": "#D71920",
  "backgroundColor2": "#1C1C1E",
  "backgroundStyle": "linear-right",
  "imagePath": "/api/files/images/00000000-0000-0000-0000-000000000000.jpg",
  "ctaText": "Browse switches",
  "linkUrl": "/catalog?type=PRODUCT",
  "ctaText2": null,
  "linkUrl2": null,
  "position": 0,
  "active": true
}
```

`BannerDto` is the same fields, with `id` number, `position` number, and `active` boolean. Null colours and styles come back as the defaults.

| Method | Path | Status |
| --- | --- | --- |
| GET | `/api/admin/banners` | 200, all banners, including inactive |
| POST | `/api/admin/banners` | 201 |
| PUT | `/api/admin/banners/{id}` | 200 |
| DELETE | `/api/admin/banners/{id}` | 204 |

## Image upload

### POST `/api/admin/uploads/image`

Admin or super admin. Body is form-data with one field:

| Key | Type | Value |
| --- | --- | --- |
| `file` | File | JPG, PNG, GIF, WebP, or AVIF. Content type must be `image/jpeg`, `image/png`, `image/gif`, `image/webp`, or `image/avif`. Max 15 MB |

**200** `UploadResponse`:

| Field | Type |
| --- | --- |
| `path` | string. Save this on a product `images` entry or a banner `imagePath`. Example: `/api/files/images/{uuid}.jpg` |
| `name` | string. Original file name |
| `size` | number. Bytes |

Fetch the bytes with `GET {{baseUrl}}{{path}}` only after the path’s file name is separated out, or call `GET /api/files/images/{name}` directly.

## Admin users

Super admin only.

`AdminRequest`:

| Field | Type | Rules |
| --- | --- | --- |
| `name` | string | Required |
| `email` | string | Required, valid email. Stored lowercase |
| `password` | string | Required on create, minimum 8 characters. On update, blank or omitted leaves the current password |

```json
{
  "name": "Store Admin",
  "email": "admin@osc-markets.com",
  "password": "ChangeMe1"
}
```

| Method | Path | Status | Result |
| --- | --- | --- | --- |
| GET | `/api/admin/admins` | 200 | Array of `AdminDto`, oldest first |
| POST | `/api/admin/admins` | 201 | `AdminDto` with role `ADMIN` |
| PUT | `/api/admin/admins/{id}` | 200 | `AdminDto` |
| DELETE | `/api/admin/admins/{id}` | 204 | Empty body |

**409** if the email is already used. **403** if the target is the super admin. **404** if the id does not exist.

## Sample catalogue

On an empty database the app inserts categories Networking, Computing, Security & Surveillance, and Power & Backup; brands Cisco, Ubiquiti, Dell, Hikvision, and APC; options Memory, Port count, Warranty term, and Resolution; and eight products and solutions. Call `GET /api/catalog/products` and use the returned ids. Do not assume id `1` still exists after data has been deleted.
