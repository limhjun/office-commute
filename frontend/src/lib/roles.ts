import type { schemas } from '@/api/types';

export type Role = schemas['Role'];

const ROLE_LABELS: Record<string, { label: string; color: string }> = {
  MANAGER: { label: '매니저', color: 'indigo' },
  MEMBER: { label: '멤버', color: 'gray' },
  COMMUTE_APPROVER: { label: '상위 승인자', color: 'grape' },
};

// 정정 처리 목록(/review)을 쓸 수 있는 역할
export const REVIEWER_ROLES: Role[] = ['MANAGER', 'COMMUTE_APPROVER'];

export function roleLabel(role: string): { label: string; color: string } {
  return ROLE_LABELS[role] ?? { label: role, color: 'gray' };
}
