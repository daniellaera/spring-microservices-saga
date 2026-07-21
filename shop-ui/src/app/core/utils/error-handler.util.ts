import { MessageService } from 'primeng/api';

export function handleHttpError(
  err: any,
  messageService: MessageService,
  summary: string = 'Error'
): void {
  const detail = err?.error?.message
    || err?.error?.error
    || 'Something went wrong. Please try again.';
  messageService.add({
    severity: 'error',
    summary,
    detail,
    life: 4000
  });
}
