import { IBM_Plex_Sans, JetBrains_Mono } from 'next/font/google';
import localFont from 'next/font/local';

export const plex = IBM_Plex_Sans({
  subsets: ['latin', 'latin-ext'],
  weight: ['400', '500', '600', '700'],
  variable: '--font-plex',
});

export const jetbrains = JetBrains_Mono({
  subsets: ['latin', 'latin-ext'],
  variable: '--font-jetbrains',
});

// Minecraft's pixel look, for the logo only; body text stays in a real text face
export const monocraft = localFont({
  src: '../app/fonts/Monocraft.woff2',
  variable: '--font-monocraft',
  display: 'swap',
});
