import Link from 'next/link';
import { Download } from '@/components/download';
import { i18n } from '@/lib/i18n';

const copy = {
  en: {
    eyebrow: 'Documentation',
    title: 'Create: New Era',
    lead: 'An addon for Create 6 on NeoForge 1.21.1. Brass Computers run Lua while a shaft turns them, and reach your machines through data ports and cables.',
    status:
      'This is 0.1.0-alpha.1, the first pre-release. Everything here works in creative. Survival progression isn’t done yet: the cable spool comes full from its recipe and some recipes are placeholders.',
    install: 'How to install',
    guide: {
      title: 'Guide',
      who: 'For players and modpack makers',
      links: [
        ['Your first program', 'guide'],
        ['The Brass Computer', 'guide/computer'],
        ['Lua API reference', 'guide/lua'],
        ['The data bus', 'guide/bus'],
        ['Cables', 'guide/cables'],
        ['Recipes', 'guide/recipes'],
      ],
    },
    dev: {
      title: 'Development',
      who: 'For people working on the mod',
      links: [
        ['Quick start', 'dev'],
        ['Building', 'dev/building'],
        ['Live reloading', 'dev/hot-reload'],
        ['Tests and datagen', 'dev/testing'],
        ['Contributing', 'dev/contributing'],
      ],
    },
    open: 'Open',
  },
  es: {
    eyebrow: 'Documentación',
    title: 'Create: New Era',
    lead: 'Un addon para Create 6 en NeoForge 1.21.1. Los ordenadores de latón ejecutan Lua mientras un eje los hace girar, y llegan a tus máquinas con puertos y cables de datos.',
    status:
      'Esta es la 0.1.0-alpha.1, la primera versión previa. Todo lo de aquí funciona en creativo. La progresión en supervivencia aún no está: la bobina de cable sale llena de su receta y algunas recetas son provisionales.',
    install: 'Cómo instalarlo',
    guide: {
      title: 'Guía',
      who: 'Para jugadores y quien monta modpacks',
      links: [
        ['Tu primer programa', 'guide'],
        ['El ordenador de latón', 'guide/computer'],
        ['Referencia de la API de Lua', 'guide/lua'],
        ['El bus de datos', 'guide/bus'],
        ['Cables', 'guide/cables'],
        ['Recetas', 'guide/recipes'],
      ],
    },
    dev: {
      title: 'Desarrollo',
      who: 'Para quien trabaja en el mod',
      links: [
        ['Empezar', 'dev'],
        ['Compilar', 'dev/building'],
        ['Recarga en caliente', 'dev/hot-reload'],
        ['Tests y datagen', 'dev/testing'],
        ['Contribuir', 'dev/contributing'],
      ],
    },
    open: 'Abrir',
  },
};

function Section({
  lang,
  section,
  href,
  image,
  open,
}: {
  lang: string;
  section: { title: string; who: string; links: string[][] };
  href: string;
  image: string;
  open: string;
}) {
  return (
    <section className="flex flex-col rounded-2xl border bg-fd-card p-5 sm:p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold tracking-[-0.015em]">{section.title}</h2>
          <p className="mt-1 text-sm text-fd-muted-foreground">{section.who}</p>
        </div>
        {/* biome-ignore lint/performance/noImgElement: pixel-art render in a static export */}
        <img src={image} alt="" width={48} height={48} className="pixelated size-12 shrink-0" />
      </div>
      <ul className="mt-5 flex flex-col">
        {section.links.map(([label, path]) => (
          <li key={path}>
            <Link
              href={`/${lang}/${path}/`}
              className="press group -mx-2 flex items-center justify-between rounded-lg px-2 py-2 text-[15px] hover:bg-fd-accent"
            >
              <span>{label}</span>
              <span aria-hidden className="text-fd-muted-foreground transition-transform group-hover:translate-x-0.5">
                →
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <Link
        href={`/${lang}/${href}/`}
        className="press mt-4 self-start rounded-lg border px-3.5 py-2 text-sm font-medium hover:bg-fd-accent"
      >
        {open} {section.title}
      </Link>
    </section>
  );
}

export default async function Home({ params }: { params: Promise<{ lang: string }> }) {
  const { lang } = await params;
  const t = copy[lang as keyof typeof copy] ?? copy.en;

  return (
    <main className="mx-auto w-full max-w-[1040px] px-4 pt-12 pb-20 sm:px-6 sm:pt-16">
      <div className="grid items-center gap-10 md:grid-cols-[1fr_auto]">
        <div className="max-w-[620px]">
          <p className="text-sm font-medium text-fd-primary">{t.eyebrow}</p>
          <h1 className="mt-2 text-[clamp(2.2rem,5vw,3.4rem)] font-semibold leading-[1.05] tracking-[-0.03em]">
            {t.title}
          </h1>
          <p className="mt-4 text-lg leading-relaxed text-fd-muted-foreground">{t.lead}</p>
          <div className="mt-7 flex flex-wrap items-center gap-4">
            <Download lang={lang} />
          </div>
          <p className="mt-3 text-sm">
            <Link href={`/${lang}/guide/install/`} className="text-fd-primary underline underline-offset-4">
              {t.install}
            </Link>
          </p>
        </div>
        {/* biome-ignore lint/performance/noImgElement: pixel-art render in a static export */}
        <img
          src="/img/blocks/brass_computer_shaft.png"
          alt=""
          width={260}
          height={260}
          className="pixelated mx-auto hidden w-[220px] md:block lg:w-[260px]"
        />
      </div>

      <p className="mt-10 max-w-[760px] rounded-xl border border-[color-mix(in_oklab,var(--copper-3)_45%,transparent)] bg-[color-mix(in_oklab,var(--copper-3)_9%,transparent)] px-4 py-3 text-sm leading-relaxed">
        {t.status}
      </p>

      <div className="mt-10 grid gap-5 md:grid-cols-2">
        <Section
          lang={lang}
          section={t.guide}
          href="guide"
          image="/img/items/create_new_era_brass_computer.png"
          open={t.open}
        />
        <Section lang={lang} section={t.dev} href="dev" image="/img/items/create_brass_casing.png" open={t.open} />
      </div>
    </main>
  );
}

export function generateStaticParams() {
  return i18n.languages.map((lang) => ({ lang }));
}
