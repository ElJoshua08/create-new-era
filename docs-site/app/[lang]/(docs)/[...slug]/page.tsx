import { DocsBody, DocsDescription, DocsPage, DocsTitle } from 'fumadocs-ui/layouts/docs/page';
import { createRelativeLink } from 'fumadocs-ui/mdx';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getMDXComponents } from '@/components/mdx';
import { gitConfig, repoUrl } from '@/lib/shared';
import { source } from '@/lib/source';

type Props = { params: Promise<{ lang: string; slug: string[] }> };

const footer = {
  en: { edit: 'Edit this page', issue: 'Found a mistake? Open an issue' },
  es: { edit: 'Editar esta página', issue: '¿Algo está mal? Abre una issue' },
};

export default async function Page({ params }: Props) {
  const { lang, slug } = await params;
  const page = source.getPage(slug, lang);
  if (!page) notFound();

  const MDX = page.data.body;
  const text = footer[lang as keyof typeof footer] ?? footer.en;

  return (
    <DocsPage toc={page.data.toc} full={page.data.full}>
      <DocsTitle>{page.data.title}</DocsTitle>
      <DocsDescription>{page.data.description}</DocsDescription>
      <DocsBody>
        <MDX components={getMDXComponents(lang, { a: createRelativeLink(source, page) })} />
      </DocsBody>
      <div className="mt-10 flex flex-wrap gap-x-6 gap-y-2 border-t pt-5 text-sm text-fd-muted-foreground">
        <a
          className="hover:text-fd-foreground"
          href={`${repoUrl}/blob/${gitConfig.branch}/docs-site/content/${page.path}`}
          target="_blank"
          rel="noreferrer"
        >
          {text.edit}
        </a>
        <a className="hover:text-fd-foreground" href={`${repoUrl}/issues`} target="_blank" rel="noreferrer">
          {text.issue}
        </a>
      </div>
    </DocsPage>
  );
}

export function generateStaticParams() {
  return source.generateParams('slug', 'lang').filter((p) => p.slug.length > 0);
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { lang, slug } = await params;
  const page = source.getPage(slug, lang);
  if (!page) notFound();
  return {
    title: page.data.title,
    description: page.data.description,
    alternates: {
      languages: Object.fromEntries(
        source.getLanguages().map(({ language }) => [language, `/${language}/${slug.join('/')}/`]),
      ),
    },
  };
}
