import type { CSSProperties, ReactNode } from 'react';
import { Suspense, lazy } from 'react';

type GlassVariant = 'water' | 'liquid';

const GlassSurfaceLiquid = lazy(() => import('./GlassSurfaceLiquid'));

export function GlassSurface({
  variant,
  className,
  style,
  children,
  radius,
  baseColor,
  borderColor,
  themeMode,
  widthMode
}: {
  variant?: GlassVariant | null;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
  radius?: number;
  baseColor?: string | null;
  borderColor?: string | null;
  themeMode?: string;
  widthMode?: 'fill' | 'intrinsic';
}) {
  const plain = (
    <div className={className} style={style}>
      {children}
    </div>
  );

  if (!variant) {
    return plain;
  }

  return (
    <Suspense fallback={plain}>
      <GlassSurfaceLiquid
        baseColor={baseColor}
        borderColor={borderColor}
        className={className}
        radius={radius}
        style={style}
        themeMode={themeMode}
        variant={variant}
        widthMode={widthMode}
      >
        {children}
      </GlassSurfaceLiquid>
    </Suspense>
  );
}
