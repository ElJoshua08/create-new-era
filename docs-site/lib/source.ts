import { loader } from 'fumadocs-core/source';
import { metaSchema, pageSchema } from 'fumadocs-core/source/schema';
import { defineDocs } from 'fumadocs-mdx/macro';
import { i18n } from './i18n';

const docs = defineDocs({
  dir: 'content',
  docs: { schema: pageSchema },
  meta: { schema: metaSchema },
});

// pages live at /<lang>/guide/... and /<lang>/dev/...; the docs have the subdomain to themselves
export const source = loader({
  baseUrl: '/',
  i18n,
  source: docs.toFumadocsSource(),
});
