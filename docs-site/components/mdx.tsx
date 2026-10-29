import { Step, Steps } from 'fumadocs-ui/components/steps';
import { Tab, Tabs } from 'fumadocs-ui/components/tabs';
import defaultMdxComponents from 'fumadocs-ui/mdx';
import type { MDXComponents } from 'mdx/types';
import { Download } from './download';
import { Recipe } from './recipe';

// lang is bound here so pages can write <Recipe id="bus_port" /> without repeating it
export function getMDXComponents(lang = 'en', components?: MDXComponents) {
  return {
    ...defaultMdxComponents,
    Steps,
    Step,
    Tabs,
    Tab,
    // biome-ignore lint/correctness/noComponentHookFactories: the wrapper only binds the page's language
    Recipe: (props: { id: string }) => <Recipe {...props} lang={lang} />,
    // biome-ignore lint/correctness/noComponentHookFactories: the wrapper only binds the page's language
    Download: () => <Download lang={lang} />,
    ...components,
  } satisfies MDXComponents;
}

export const useMDXComponents = () => getMDXComponents();

declare global {
  type MDXProvidedComponents = ReturnType<typeof getMDXComponents>;
}
