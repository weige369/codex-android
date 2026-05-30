import { Suspense, lazy } from 'react';

const MarkdownRendererImpl = lazy(() => import('./MarkdownRendererImpl'));

export function MarkdownRenderer({
  content,
  className
}: {
  content: string;
  className?: string;
}) {
  if (!content.trim()) {
    return null;
  }

  return (
    <Suspense
      fallback={
        <div className={className ?? 'markdown-block'}>
          <p className="markdown-paragraph">{content}</p>
        </div>
      }
    >
      <MarkdownRendererImpl content={content} className={className} />
    </Suspense>
  );
}
