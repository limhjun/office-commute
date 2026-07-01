import type { components } from '@/api/schema';

// `openapi-typescript` (gen:api) 는 `components["schemas"]` 만 내보내므로,
// 소비 코드가 짧게 쓸 수 있도록 별칭을 여기서 한 번만 정의한다.
// schema.d.ts 는 생성 산출물이라 손대지 않는다.
export type schemas = components['schemas'];
