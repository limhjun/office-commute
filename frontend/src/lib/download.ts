import { ApiError } from './errors';

/**
 * Excel 리포트는 JSON 이 아니라 xlsx 바이너리다.
 * fetch 로 blob 을 받아 저장하고, 세션 만료/권한/공휴일 실패를 에러로 처리한다.
 * Content-Disposition 의 filename*(RFC 5987) 을 우선 사용한다.
 */
export async function downloadFile(url: string, fallbackName: string): Promise<void> {
  const res = await fetch(url, { credentials: 'include' });
  if (!res.ok) {
    let body: { code?: string; message?: string } | undefined;
    try { body = await res.json(); } catch { /* 바이너리/빈 응답 */ }
    throw new ApiError(res.status, body as never);
  }

  const blob = await res.blob();
  const name = filenameFromDisposition(res.headers.get('Content-Disposition')) ?? fallbackName;
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}

function filenameFromDisposition(header: string | null): string | undefined {
  if (!header) return undefined;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star?.[1]) return decodeURIComponent(star[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain?.[1];
}
