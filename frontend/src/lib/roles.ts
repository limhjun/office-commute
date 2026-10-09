import type { schemas } from '@/api/types';

export type Role = schemas['Role'];

const ROLE_LABELS: Record<string, { label: string; color: string }> = {
  MANAGER: { label: '매니저', color: 'indigo' },
  MEMBER: { label: '멤버', color: 'gray' },
};

export function roleLabel(role: string): { label: string; color: string } {
  return ROLE_LABELS[role] ?? { label: role, color: 'gray' };
}
