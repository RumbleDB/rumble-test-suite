import { JSX, splitProps } from "solid-js";

type IconProps = JSX.SvgSVGAttributes<SVGSVGElement> & {
  size?: number;
};

function BaseIcon(props: IconProps & { children: JSX.Element }) {
  const [local, rest] = splitProps(props, ["size", "children", "stroke", "stroke-width", "fill", "viewBox"]);
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={local.viewBox || "0 0 24 24"}
      width={local.size || 18}
      height={local.size || 18}
      fill={local.fill || "none"}
      stroke={local.stroke || "currentColor"}
      stroke-width={local["stroke-width"] || 2}
      stroke-linecap="round"
      stroke-linejoin="round"
      {...rest}
    >
      {local.children}
    </svg>
  );
}

export function ChevronLeft(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m15 18-6-6 6-6" />
    </BaseIcon>
  );
}

export function ChevronRight(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="m9 18 6-6-6-6" />
    </BaseIcon>
  );
}

export function Copy(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
      <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
    </BaseIcon>
  );
}

export function Check(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M20 6 9 17l-5-5" />
    </BaseIcon>
  );
}

export function Search(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="11" cy="11" r="8" />
      <path d="m21 21-4.3-4.3" />
    </BaseIcon>
  );
}

export function AlertCircle(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 8v4" />
      <path d="M12 16h.01" />
    </BaseIcon>
  );
}

export function CheckCircle(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <circle cx="12" cy="12" r="10" />
      <path d="m9 12 2 2 4-4" />
    </BaseIcon>
  );
}

export function Terminal(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <polyline points="4 17 10 11 4 5" />
      <line x1="12" x2="20" y1="19" y2="19" />
    </BaseIcon>
  );
}

export function ShieldAlert(props: IconProps) {
  return (
    <BaseIcon {...props}>
      <path d="M20 13c0 5-3.5 7.5-7.66 9.7a1 1 0 0 1-.68 0C7.5 20.5 4 18 4 13V6a1 1 0 0 1 .76-.97l8-2a1 1 0 0 1 .48 0l8 2c.44.1.76.5.76.97Z" />
      <path d="M12 8v4" />
      <path d="M12 16h.01" />
    </BaseIcon>
  );
}

export function Play(props: IconProps) {
  return (
    <BaseIcon {...props} fill="currentColor">
      <polygon points="6 3 20 12 6 21 6 3" />
    </BaseIcon>
  );
}
