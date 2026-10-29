export function Logo() {
  return (
    <span className="inline-flex items-center gap-2">
      {/* biome-ignore lint/performance/noImgElement: static export, the icon is a 64px pixel-art render */}
      <img
        src="/img/items/create_new_era_brass_computer.png"
        alt=""
        width={22}
        height={22}
        className="pixelated size-[22px]"
      />
      <span className="pixel text-[15px] leading-none text-fd-foreground">
        New Era<span className="text-fd-primary"> docs</span>
      </span>
    </span>
  );
}
