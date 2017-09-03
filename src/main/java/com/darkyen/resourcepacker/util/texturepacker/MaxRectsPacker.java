/* *****************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.darkyen.resourcepacker.util.texturepacker;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Sort;

import java.util.Comparator;

/**
 * Packs pages of images using the maximal rectangles bin packing algorithm by Jukka Jylänki. A brute force binary search is
 * used to pack into the smallest bin possible.
 *
 * @author Nathan Sweet
 */
public class MaxRectsPacker implements MultiScaleTexturePacker.Packer {
    private RectComparator rectComparator = new RectComparator();
    private FreeRectChoiceHeuristic[] methods = FreeRectChoiceHeuristic.values();
    private MaxRects maxRects = new MaxRects();
    MultiScaleTexturePacker.Settings settings;
    private Sort sort = new Sort();

    public MaxRectsPacker(MultiScaleTexturePacker.Settings settings) {
        this.settings = settings;
        if (settings.minWidth > settings.maxWidth)
            throw new RuntimeException("Page min width cannot be higher than max width.");
        if (settings.minHeight > settings.maxHeight)
            throw new RuntimeException("Page min height cannot be higher than max height.");
    }

    public Array<MultiScaleTexturePacker.Page> pack(Array<MultiScaleTexturePacker.Rect> inputRects) {
        for (int i = 0, nn = inputRects.size; i < nn; i++) {
            MultiScaleTexturePacker.Rect rect = inputRects.get(i);
            rect.pageWidth += settings.paddingX;
            rect.pageHeight += settings.paddingY;
        }

        if (settings.fast) {
            if (settings.rotation) {
                // Sort by longest side if rotation is enabled.
                sort.sort(inputRects, new Comparator<MultiScaleTexturePacker.Rect>() {
                    public int compare(MultiScaleTexturePacker.Rect o1, MultiScaleTexturePacker.Rect o2) {
                        int n1 = o1.pageWidth > o1.pageHeight ? o1.pageWidth : o1.pageHeight;
                        int n2 = o2.pageWidth > o2.pageHeight ? o2.pageWidth : o2.pageHeight;
                        return n2 - n1;
                    }
                });
            } else {
                // Sort only by width (largest to smallest) if rotation is disabled.
                sort.sort(inputRects, new Comparator<MultiScaleTexturePacker.Rect>() {
                    public int compare(MultiScaleTexturePacker.Rect o1, MultiScaleTexturePacker.Rect o2) {
                        return o2.pageWidth - o1.pageWidth;
                    }
                });
            }
        }

        Array<MultiScaleTexturePacker.Page> pages = new Array<>();
        while (inputRects.size > 0)

        {
            MultiScaleTexturePacker.Page result = packPage(inputRects);
            pages.add(result);
            inputRects = result.remainingRects;
        }
        return pages;

    }

    private MultiScaleTexturePacker.Page packPage(Array<MultiScaleTexturePacker.Rect> inputRects) {
        int paddingX = settings.paddingX, paddingY = settings.paddingY;
        float maxWidth = settings.maxWidth, maxHeight = settings.maxHeight;
        int edgePaddingX = 0, edgePaddingY = 0;
        if (settings.edgePadding) {
            if (settings.duplicatePadding) { // If duplicatePadding, edges get only half padding.
                maxWidth -= paddingX;
                maxHeight -= paddingY;
            } else {
                maxWidth -= paddingX * 2;
                maxHeight -= paddingY * 2;
                edgePaddingX = paddingX;
                edgePaddingY = paddingY;
            }
        }

        // Find min size.
        int minWidth = Integer.MAX_VALUE, minHeight = Integer.MAX_VALUE;
        for (int i = 0, nn = inputRects.size; i < nn; i++) {
            MultiScaleTexturePacker.Rect rect = inputRects.get(i);
            minWidth = Math.min(minWidth, rect.pageWidth);
            minHeight = Math.min(minHeight, rect.pageHeight);
            float width = rect.pageWidth - paddingX, height = rect.pageHeight - paddingY;
            if (settings.rotation) {
                if ((width > maxWidth || height > maxHeight) && (width > maxHeight || height > maxWidth)) {
                    String paddingMessage = (edgePaddingX > 0 || edgePaddingY > 0) ? (" and edge padding " + paddingX + "," + paddingY)
                            : "";
                    throw new RuntimeException("Image does not fit with max page size " + settings.maxWidth + "x" + settings.maxHeight
                            + paddingMessage + ": " + rect.source.name + "[" + width + "," + height + "]");
                }
            } else {
                if (width > maxWidth) {
                    String paddingMessage = edgePaddingX > 0 ? (" and X edge padding " + paddingX) : "";
                    throw new RuntimeException("Image does not fit with max page width " + settings.maxWidth + paddingMessage + ": "
                            + rect.source.name + "[" + width + "," + height + "]");
                }
                //noinspection ConstantConditions
                if (height > maxHeight && (!settings.rotation || width > maxHeight)) {
                    String paddingMessage = edgePaddingY > 0 ? (" and Y edge padding " + paddingY) : "";
                    throw new RuntimeException("Image does not fit in max page height " + settings.maxHeight + paddingMessage + ": "
                            + rect.source.name + "[" + width + "," + height + "]");
                }
            }
        }
        minWidth = Math.max(minWidth, settings.minWidth);
        minHeight = Math.max(minHeight, settings.minHeight);

        if (!settings.silent) System.out.print("Packing");

        // Find the minimal page size that fits all rects.
        MultiScaleTexturePacker.Page bestResult = null;
        if (settings.square) {
            int minSize = Math.max(minWidth, minHeight);
            int maxSize = Math.min(settings.maxWidth, settings.maxHeight);
            BinarySearch sizeSearch = new BinarySearch(minSize, maxSize, settings.fast ? 25 : 15, settings.pot);
            int size = sizeSearch.reset(), i = 0;
            while (size != -1) {
                MultiScaleTexturePacker.Page result = packAtSize(true, size - edgePaddingX, size - edgePaddingY, inputRects);
                if (!settings.silent) {
                    if (++i % 70 == 0) System.out.println();
                    System.out.print(".");
                }
                bestResult = getBest(bestResult, result);
                size = sizeSearch.next(result == null);
            }
            if (!settings.silent) System.out.println();
            // Rects don't fit on one page. Fill a whole page and return.
            if (bestResult == null)
                bestResult = packAtSize(false, maxSize - edgePaddingX, maxSize - edgePaddingY, inputRects);
            sort.sort(bestResult.outputRects, rectComparator);
            bestResult.width = Math.max(bestResult.width, bestResult.height);
            bestResult.height = Math.max(bestResult.width, bestResult.height);
            return bestResult;
        } else {
            BinarySearch widthSearch = new BinarySearch(minWidth, settings.maxWidth, settings.fast ? 25 : 15, settings.pot);
            BinarySearch heightSearch = new BinarySearch(minHeight, settings.maxHeight, settings.fast ? 25 : 15, settings.pot);
            int width = widthSearch.reset(), i = 0;
            int height = settings.square ? width : heightSearch.reset();
            while (true) {
                MultiScaleTexturePacker.Page bestWidthResult = null;
                while (width != -1) {
                    MultiScaleTexturePacker.Page result = packAtSize(true, width - edgePaddingX, height - edgePaddingY, inputRects);
                    if (!settings.silent) {
                        if (++i % 70 == 0) System.out.println();
                        System.out.print(".");
                    }
                    bestWidthResult = getBest(bestWidthResult, result);
                    width = widthSearch.next(result == null);
                    if (settings.square) //noinspection SuspiciousNameCombination
                        height = width;
                }
                bestResult = getBest(bestResult, bestWidthResult);
                if (settings.square) break;
                height = heightSearch.next(bestWidthResult == null);
                if (height == -1) break;
                width = widthSearch.reset();
            }
            if (!settings.silent) System.out.println();
            // Rects don't fit on one page. Fill a whole page and return.
            if (bestResult == null)
                bestResult = packAtSize(false, settings.maxWidth - edgePaddingX, settings.maxHeight - edgePaddingY, inputRects);
            sort.sort(bestResult.outputRects, rectComparator);
            return bestResult;
        }
    }

    /**
     * @param fully If true, the only results that pack all rects will be considered. If false, all results are considered, not
     *              all rects may be packed.
     */
    private MultiScaleTexturePacker.Page packAtSize(boolean fully, int width, int height, Array<MultiScaleTexturePacker.Rect> inputRects) {
        MultiScaleTexturePacker.Page bestResult = null;
        for (FreeRectChoiceHeuristic method : methods) {
            maxRects.init(width, height);
            MultiScaleTexturePacker.Page result;
            if (!settings.fast) {
                result = maxRects.pack(inputRects, method);
            } else {
                Array<MultiScaleTexturePacker.Rect> remaining = new Array<>();
                for (int ii = 0, nn = inputRects.size; ii < nn; ii++) {
                    MultiScaleTexturePacker.Rect rect = inputRects.get(ii);
                    if (maxRects.insert(rect, method) == null) {
                        while (ii < nn)
                            remaining.add(inputRects.get(ii++));
                    }
                }
                result = maxRects.getResult();
                result.remainingRects = remaining;
            }
            if (fully && result.remainingRects.size > 0) continue;
            if (result.outputRects.size == 0) continue;
            bestResult = getBest(bestResult, result);
        }
        return bestResult;
    }

    private MultiScaleTexturePacker.Page getBest(MultiScaleTexturePacker.Page result1, MultiScaleTexturePacker.Page result2) {
        if (result1 == null) return result2;
        if (result2 == null) return result1;
        return result1.occupancy > result2.occupancy ? result1 : result2;
    }

    static class BinarySearch {
        int min, max, fuzziness, low, high, current;
        boolean pot;

        public BinarySearch(int min, int max, int fuzziness, boolean pot) {
            this.pot = pot;
            this.fuzziness = pot ? 0 : fuzziness;
            this.min = pot ? (int) (Math.log(MathUtils.nextPowerOfTwo(min)) / Math.log(2)) : min;
            this.max = pot ? (int) (Math.log(MathUtils.nextPowerOfTwo(max)) / Math.log(2)) : max;
        }

        public int reset() {
            low = min;
            high = max;
            current = (low + high) >>> 1;
            return pot ? (int) Math.pow(2, current) : current;
        }

        public int next(boolean result) {
            if (low >= high) return -1;
            if (result)
                low = current + 1;
            else
                high = current - 1;
            current = (low + high) >>> 1;
            if (Math.abs(low - high) < fuzziness) return -1;
            return pot ? (int) Math.pow(2, current) : current;
        }
    }

    /**
     * Maximal rectangles bin packing algorithm. Adapted from this C++ public domain source:
     * http://clb.demon.fi/projects/even-more-rectangle-bin-packing
     *
     * @author Jukka Jyl�nki
     * @author Nathan Sweet
     */
    class MaxRects {
        private int binWidth;
        private int binHeight;
        private final Array<MultiScaleTexturePacker.Rect> usedRectangles = new Array<>();
        private final Array<MultiScaleTexturePacker.Rect> freeRectangles = new Array<>();

        public void init(int width, int height) {
            binWidth = width;
            binHeight = height;

            usedRectangles.clear();
            freeRectangles.clear();
            MultiScaleTexturePacker.Rect n = new MultiScaleTexturePacker.Rect();
            n.pageX = 0;
            n.pageY = 0;
            n.pageWidth = width;
            n.pageHeight = height;
            freeRectangles.add(n);
        }

        /**
         * Packs a single image. Order is defined externally.
         */
        public MultiScaleTexturePacker.Rect insert(MultiScaleTexturePacker.Rect rect, FreeRectChoiceHeuristic method) {
            MultiScaleTexturePacker.Rect newNode = scoreRect(rect, method);
            if (newNode.pageHeight == 0) return null;

            int numRectanglesToProcess = freeRectangles.size;
            for (int i = 0; i < numRectanglesToProcess; ++i) {
                if (splitFreeNode(freeRectangles.get(i), newNode)) {
                    freeRectangles.removeIndex(i);
                    --i;
                    --numRectanglesToProcess;
                }
            }

            pruneFreeList();

            MultiScaleTexturePacker.Rect bestNode = new MultiScaleTexturePacker.Rect();
            bestNode.set(rect);
            bestNode.score1 = newNode.score1;
            bestNode.score2 = newNode.score2;
            bestNode.pageX = newNode.pageX;
            bestNode.pageY = newNode.pageY;
            bestNode.pageWidth = newNode.pageWidth;
            bestNode.pageHeight = newNode.pageHeight;
            bestNode.rotated = newNode.rotated;

            usedRectangles.add(bestNode);
            return bestNode;
        }

        /**
         * For each rectangle, packs each one then chooses the best and packs that. Slow!
         */
        public MultiScaleTexturePacker.Page pack(Array<MultiScaleTexturePacker.Rect> rects, FreeRectChoiceHeuristic method) {
            rects = new Array<>(rects);
            while (rects.size > 0) {
                int bestRectIndex = -1;
                MultiScaleTexturePacker.Rect bestNode = new MultiScaleTexturePacker.Rect();
                bestNode.score1 = Integer.MAX_VALUE;
                bestNode.score2 = Integer.MAX_VALUE;

                // Find the next rectangle that packs best.
                for (int i = 0; i < rects.size; i++) {
                    MultiScaleTexturePacker.Rect newNode = scoreRect(rects.get(i), method);
                    if (newNode.score1 < bestNode.score1 || (newNode.score1 == bestNode.score1 && newNode.score2 < bestNode.score2)) {
                        bestNode.set(rects.get(i));
                        bestNode.score1 = newNode.score1;
                        bestNode.score2 = newNode.score2;
                        bestNode.pageX = newNode.pageX;
                        bestNode.pageY = newNode.pageY;
                        bestNode.pageWidth = newNode.pageWidth;
                        bestNode.pageHeight = newNode.pageHeight;
                        bestNode.rotated = newNode.rotated;
                        bestRectIndex = i;
                    }
                }

                if (bestRectIndex == -1) break;

                placeRect(bestNode);
                rects.removeIndex(bestRectIndex);
            }

            MultiScaleTexturePacker.Page result = getResult();
            result.remainingRects = rects;
            return result;
        }

        public MultiScaleTexturePacker.Page getResult() {
            int w = 0, h = 0;
            for (int i = 0; i < usedRectangles.size; i++) {
                MultiScaleTexturePacker.Rect rect = usedRectangles.get(i);
                w = Math.max(w, rect.pageX + rect.pageWidth);
                h = Math.max(h, rect.pageY + rect.pageHeight);
            }
            MultiScaleTexturePacker.Page result = new MultiScaleTexturePacker.Page();
            result.outputRects = new Array<>(usedRectangles);
            result.occupancy = getOccupancy();
            result.width = w;
            result.height = h;
            return result;
        }

        private void placeRect(MultiScaleTexturePacker.Rect node) {
            int numRectanglesToProcess = freeRectangles.size;
            for (int i = 0; i < numRectanglesToProcess; i++) {
                if (splitFreeNode(freeRectangles.get(i), node)) {
                    freeRectangles.removeIndex(i);
                    --i;
                    --numRectanglesToProcess;
                }
            }

            pruneFreeList();

            usedRectangles.add(node);
        }

        private MultiScaleTexturePacker.Rect scoreRect(MultiScaleTexturePacker.Rect rect, FreeRectChoiceHeuristic method) {
            int width = rect.pageWidth;
            int height = rect.pageHeight;
            int rotatedWidth = height - settings.paddingY + settings.paddingX;
            int rotatedHeight = width - settings.paddingX + settings.paddingY;
            boolean rotate = rect.canRotate() && settings.rotation;

            MultiScaleTexturePacker.Rect newNode = null;
            switch (method) {
                case BestShortSideFit:
                    newNode = findPositionForNewNodeBestShortSideFit(width, height, rotatedWidth, rotatedHeight, rotate);
                    break;
                case BottomLeftRule:
                    newNode = findPositionForNewNodeBottomLeft(width, height, rotatedWidth, rotatedHeight, rotate);
                    break;
                case ContactPointRule:
                    newNode = findPositionForNewNodeContactPoint(width, height, rotatedWidth, rotatedHeight, rotate);
                    newNode.score1 = -newNode.score1; // Reverse since we are minimizing, but for contact point score bigger is better.
                    break;
                case BestLongSideFit:
                    newNode = findPositionForNewNodeBestLongSideFit(width, height, rotatedWidth, rotatedHeight, rotate);
                    break;
                case BestAreaFit:
                    newNode = findPositionForNewNodeBestAreaFit(width, height, rotatedWidth, rotatedHeight, rotate);
                    break;
            }

            // Cannot fit the current rectangle.
            if (newNode.pageHeight == 0) {
                newNode.score1 = Integer.MAX_VALUE;
                newNode.score2 = Integer.MAX_VALUE;
            }

            return newNode;
        }

        // / Computes the ratio of used surface area.
        private float getOccupancy() {
            int usedSurfaceArea = 0;
            for (int i = 0; i < usedRectangles.size; i++)
                usedSurfaceArea += usedRectangles.get(i).pageWidth * usedRectangles.get(i).pageHeight;
            return (float) usedSurfaceArea / (binWidth * binHeight);
        }

        private MultiScaleTexturePacker.Rect findPositionForNewNodeBottomLeft(int width, int height, int rotatedWidth, int rotatedHeight, boolean rotate) {
            MultiScaleTexturePacker.Rect bestNode = new MultiScaleTexturePacker.Rect();

            bestNode.score1 = Integer.MAX_VALUE; // best y, score2 is best x

            for (int i = 0; i < freeRectangles.size; i++) {
                // Try to place the rectangle in upright (non-rotated) orientation.
                if (freeRectangles.get(i).pageWidth >= width && freeRectangles.get(i).pageHeight >= height) {
                    int topSideY = freeRectangles.get(i).pageY + height;
                    if (topSideY < bestNode.score1 || (topSideY == bestNode.score1 && freeRectangles.get(i).pageX < bestNode.score2)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = width;
                        bestNode.pageHeight = height;
                        bestNode.score1 = topSideY;
                        bestNode.score2 = freeRectangles.get(i).pageX;
                        bestNode.rotated = false;
                    }
                }
                if (rotate && freeRectangles.get(i).pageWidth >= rotatedWidth && freeRectangles.get(i).pageHeight >= rotatedHeight) {
                    int topSideY = freeRectangles.get(i).pageY + rotatedHeight;
                    if (topSideY < bestNode.score1 || (topSideY == bestNode.score1 && freeRectangles.get(i).pageX < bestNode.score2)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = rotatedWidth;
                        bestNode.pageHeight = rotatedHeight;
                        bestNode.score1 = topSideY;
                        bestNode.score2 = freeRectangles.get(i).pageX;
                        bestNode.rotated = true;
                    }
                }
            }
            return bestNode;
        }

        private MultiScaleTexturePacker.Rect findPositionForNewNodeBestShortSideFit(int width, int height, int rotatedWidth, int rotatedHeight,
                                                                                    boolean rotate) {
            MultiScaleTexturePacker.Rect bestNode = new MultiScaleTexturePacker.Rect();
            bestNode.score1 = Integer.MAX_VALUE;

            for (int i = 0; i < freeRectangles.size; i++) {
                // Try to place the rectangle in upright (non-rotated) orientation.
                if (freeRectangles.get(i).pageWidth >= width && freeRectangles.get(i).pageHeight >= height) {
                    int leftoverHoriz = Math.abs(freeRectangles.get(i).pageWidth - width);
                    int leftoverVert = Math.abs(freeRectangles.get(i).pageHeight - height);
                    int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
                    int longSideFit = Math.max(leftoverHoriz, leftoverVert);

                    if (shortSideFit < bestNode.score1 || (shortSideFit == bestNode.score1 && longSideFit < bestNode.score2)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = width;
                        bestNode.pageHeight = height;
                        bestNode.score1 = shortSideFit;
                        bestNode.score2 = longSideFit;
                        bestNode.rotated = false;
                    }
                }

                if (rotate && freeRectangles.get(i).pageWidth >= rotatedWidth && freeRectangles.get(i).pageHeight >= rotatedHeight) {
                    int flippedLeftoverHoriz = Math.abs(freeRectangles.get(i).pageWidth - rotatedWidth);
                    int flippedLeftoverVert = Math.abs(freeRectangles.get(i).pageHeight - rotatedHeight);
                    int flippedShortSideFit = Math.min(flippedLeftoverHoriz, flippedLeftoverVert);
                    int flippedLongSideFit = Math.max(flippedLeftoverHoriz, flippedLeftoverVert);

                    if (flippedShortSideFit < bestNode.score1
                            || (flippedShortSideFit == bestNode.score1 && flippedLongSideFit < bestNode.score2)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = rotatedWidth;
                        bestNode.pageHeight = rotatedHeight;
                        bestNode.score1 = flippedShortSideFit;
                        bestNode.score2 = flippedLongSideFit;
                        bestNode.rotated = true;
                    }
                }
            }

            return bestNode;
        }

        private MultiScaleTexturePacker.Rect findPositionForNewNodeBestLongSideFit(int width, int height, int rotatedWidth, int rotatedHeight,
                                                                                   boolean rotate) {
            MultiScaleTexturePacker.Rect bestNode = new MultiScaleTexturePacker.Rect();

            bestNode.score2 = Integer.MAX_VALUE;

            for (int i = 0; i < freeRectangles.size; i++) {
                // Try to place the rectangle in upright (non-rotated) orientation.
                if (freeRectangles.get(i).pageWidth >= width && freeRectangles.get(i).pageHeight >= height) {
                    int leftoverHoriz = Math.abs(freeRectangles.get(i).pageWidth - width);
                    int leftoverVert = Math.abs(freeRectangles.get(i).pageHeight - height);
                    int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
                    int longSideFit = Math.max(leftoverHoriz, leftoverVert);

                    if (longSideFit < bestNode.score2 || (longSideFit == bestNode.score2 && shortSideFit < bestNode.score1)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = width;
                        bestNode.pageHeight = height;
                        bestNode.score1 = shortSideFit;
                        bestNode.score2 = longSideFit;
                        bestNode.rotated = false;
                    }
                }

                if (rotate && freeRectangles.get(i).pageWidth >= rotatedWidth && freeRectangles.get(i).pageHeight >= rotatedHeight) {
                    int leftoverHoriz = Math.abs(freeRectangles.get(i).pageWidth - rotatedWidth);
                    int leftoverVert = Math.abs(freeRectangles.get(i).pageHeight - rotatedHeight);
                    int shortSideFit = Math.min(leftoverHoriz, leftoverVert);
                    int longSideFit = Math.max(leftoverHoriz, leftoverVert);

                    if (longSideFit < bestNode.score2 || (longSideFit == bestNode.score2 && shortSideFit < bestNode.score1)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = rotatedWidth;
                        bestNode.pageHeight = rotatedHeight;
                        bestNode.score1 = shortSideFit;
                        bestNode.score2 = longSideFit;
                        bestNode.rotated = true;
                    }
                }
            }
            return bestNode;
        }

        private MultiScaleTexturePacker.Rect findPositionForNewNodeBestAreaFit(int width, int height, int rotatedWidth, int rotatedHeight,
                                                                               boolean rotate) {
            MultiScaleTexturePacker.Rect bestNode = new MultiScaleTexturePacker.Rect();

            bestNode.score1 = Integer.MAX_VALUE; // best area fit, score2 is best short side fit

            for (int i = 0; i < freeRectangles.size; i++) {
                int areaFit = freeRectangles.get(i).pageWidth * freeRectangles.get(i).pageHeight - width * height;

                // Try to place the rectangle in upright (non-rotated) orientation.
                if (freeRectangles.get(i).pageWidth >= width && freeRectangles.get(i).pageHeight >= height) {
                    int leftoverHoriz = Math.abs(freeRectangles.get(i).pageWidth - width);
                    int leftoverVert = Math.abs(freeRectangles.get(i).pageHeight - height);
                    int shortSideFit = Math.min(leftoverHoriz, leftoverVert);

                    if (areaFit < bestNode.score1 || (areaFit == bestNode.score1 && shortSideFit < bestNode.score2)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = width;
                        bestNode.pageHeight = height;
                        bestNode.score2 = shortSideFit;
                        bestNode.score1 = areaFit;
                        bestNode.rotated = false;
                    }
                }

                if (rotate && freeRectangles.get(i).pageWidth >= rotatedWidth && freeRectangles.get(i).pageHeight >= rotatedHeight) {
                    int leftoverHoriz = Math.abs(freeRectangles.get(i).pageWidth - rotatedWidth);
                    int leftoverVert = Math.abs(freeRectangles.get(i).pageHeight - rotatedHeight);
                    int shortSideFit = Math.min(leftoverHoriz, leftoverVert);

                    if (areaFit < bestNode.score1 || (areaFit == bestNode.score1 && shortSideFit < bestNode.score2)) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = rotatedWidth;
                        bestNode.pageHeight = rotatedHeight;
                        bestNode.score2 = shortSideFit;
                        bestNode.score1 = areaFit;
                        bestNode.rotated = true;
                    }
                }
            }
            return bestNode;
        }

        // / Returns 0 if the two intervals i1 and i2 are disjoint, or the length of their overlap otherwise.
        private int commonIntervalLength(int i1start, int i1end, int i2start, int i2end) {
            if (i1end < i2start || i2end < i1start) return 0;
            return Math.min(i1end, i2end) - Math.max(i1start, i2start);
        }

        private int contactPointScoreNode(int x, int y, int width, int height) {
            int score = 0;

            if (x == 0 || x + width == binWidth) score += height;
            if (y == 0 || y + height == binHeight) score += width;

            for (int i = 0; i < usedRectangles.size; i++) {
                if (usedRectangles.get(i).pageX == x + width || usedRectangles.get(i).pageX + usedRectangles.get(i).pageWidth == x)
                    score += commonIntervalLength(usedRectangles.get(i).pageY, usedRectangles.get(i).pageY + usedRectangles.get(i).pageHeight, y,
                            y + height);
                if (usedRectangles.get(i).pageY == y + height || usedRectangles.get(i).pageY + usedRectangles.get(i).pageHeight == y)
                    score += commonIntervalLength(usedRectangles.get(i).pageX, usedRectangles.get(i).pageX + usedRectangles.get(i).pageWidth, x,
                            x + width);
            }
            return score;
        }

        private MultiScaleTexturePacker.Rect findPositionForNewNodeContactPoint(int width, int height, int rotatedWidth, int rotatedHeight,
                                                                                boolean rotate) {
            MultiScaleTexturePacker.Rect bestNode = new MultiScaleTexturePacker.Rect();

            bestNode.score1 = -1; // best contact score

            for (int i = 0; i < freeRectangles.size; i++) {
                // Try to place the rectangle in upright (non-rotated) orientation.
                if (freeRectangles.get(i).pageWidth >= width && freeRectangles.get(i).pageHeight >= height) {
                    int score = contactPointScoreNode(freeRectangles.get(i).pageX, freeRectangles.get(i).pageY, width, height);
                    if (score > bestNode.score1) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = width;
                        bestNode.pageHeight = height;
                        bestNode.score1 = score;
                        bestNode.rotated = false;
                    }
                }
                if (rotate && freeRectangles.get(i).pageWidth >= rotatedWidth && freeRectangles.get(i).pageHeight >= rotatedHeight) {
                    // This was width,height -- bug fixed?
                    int score = contactPointScoreNode(freeRectangles.get(i).pageX, freeRectangles.get(i).pageY, rotatedWidth, rotatedHeight);
                    if (score > bestNode.score1) {
                        bestNode.pageX = freeRectangles.get(i).pageX;
                        bestNode.pageY = freeRectangles.get(i).pageY;
                        bestNode.pageWidth = rotatedWidth;
                        bestNode.pageHeight = rotatedHeight;
                        bestNode.score1 = score;
                        bestNode.rotated = true;
                    }
                }
            }
            return bestNode;
        }

        private boolean splitFreeNode(MultiScaleTexturePacker.Rect freeNode, MultiScaleTexturePacker.Rect usedNode) {
            // Test with SAT if the rectangles even intersect.
            if (usedNode.pageX >= freeNode.pageX + freeNode.pageWidth || usedNode.pageX + usedNode.pageWidth <= freeNode.pageX
                    || usedNode.pageY >= freeNode.pageY + freeNode.pageHeight || usedNode.pageY + usedNode.pageHeight <= freeNode.pageY)
                return false;

            if (usedNode.pageX < freeNode.pageX + freeNode.pageWidth && usedNode.pageX + usedNode.pageWidth > freeNode.pageX) {
                // New node at the top side of the used node.
                if (usedNode.pageY > freeNode.pageY && usedNode.pageY < freeNode.pageY + freeNode.pageHeight) {
                    MultiScaleTexturePacker.Rect newNode = new MultiScaleTexturePacker.Rect(freeNode);
                    newNode.pageHeight = usedNode.pageY - newNode.pageY;
                    freeRectangles.add(newNode);
                }

                // New node at the bottom side of the used node.
                if (usedNode.pageY + usedNode.pageHeight < freeNode.pageY + freeNode.pageHeight) {
                    MultiScaleTexturePacker.Rect newNode = new MultiScaleTexturePacker.Rect(freeNode);
                    newNode.pageY = usedNode.pageY + usedNode.pageHeight;
                    newNode.pageHeight = freeNode.pageY + freeNode.pageHeight - (usedNode.pageY + usedNode.pageHeight);
                    freeRectangles.add(newNode);
                }
            }

            if (usedNode.pageY < freeNode.pageY + freeNode.pageHeight && usedNode.pageY + usedNode.pageHeight > freeNode.pageY) {
                // New node at the left side of the used node.
                if (usedNode.pageX > freeNode.pageX && usedNode.pageX < freeNode.pageX + freeNode.pageWidth) {
                    MultiScaleTexturePacker.Rect newNode = new MultiScaleTexturePacker.Rect(freeNode);
                    newNode.pageWidth = usedNode.pageX - newNode.pageX;
                    freeRectangles.add(newNode);
                }

                // New node at the right side of the used node.
                if (usedNode.pageX + usedNode.pageWidth < freeNode.pageX + freeNode.pageWidth) {
                    MultiScaleTexturePacker.Rect newNode = new MultiScaleTexturePacker.Rect(freeNode);
                    newNode.pageX = usedNode.pageX + usedNode.pageWidth;
                    newNode.pageWidth = freeNode.pageX + freeNode.pageWidth - (usedNode.pageX + usedNode.pageWidth);
                    freeRectangles.add(newNode);
                }
            }

            return true;
        }

        private void pruneFreeList() {
            /*
			 * /// Would be nice to do something like this, to avoid a Theta(n^2) loop through each pair. /// But unfortunately it
			 * doesn't quite cut it, since we also want to detect containment. /// Perhaps there's another way to do this faster than
			 * Theta(n^2).
			 * 
			 * if (freeRectangles.size > 0) clb::sort::QuickSort(&freeRectangles[0], freeRectangles.size, NodeSortCmp);
			 * 
			 * for(int i = 0; i < freeRectangles.size-1; i++) if (freeRectangles[i].x == freeRectangles[i+1].x && freeRectangles[i].y
			 * == freeRectangles[i+1].y && freeRectangles[i].width == freeRectangles[i+1].width && freeRectangles[i].height ==
			 * freeRectangles[i+1].height) { freeRectangles.erase(freeRectangles.begin() + i); --i; }
			 */

            // Go through each pair and remove any rectangle that is redundant.
            for (int i = 0; i < freeRectangles.size; i++)
                for (int j = i + 1; j < freeRectangles.size; ++j) {
                    if (isContainedIn(freeRectangles.get(i), freeRectangles.get(j))) {
                        freeRectangles.removeIndex(i);
                        --i;
                        break;
                    }
                    if (isContainedIn(freeRectangles.get(j), freeRectangles.get(i))) {
                        freeRectangles.removeIndex(j);
                        --j;
                    }
                }
        }

        private boolean isContainedIn(MultiScaleTexturePacker.Rect a, MultiScaleTexturePacker.Rect b) {
            return a.pageX >= b.pageX && a.pageY >= b.pageY && a.pageX + a.pageWidth <= b.pageX + b.pageWidth && a.pageY + a.pageHeight <= b.pageY + b.pageHeight;
        }
    }

    public enum FreeRectChoiceHeuristic {
        // BSSF: Positions the rectangle against the short side of a free rectangle into which it fits the best.
        BestShortSideFit,
        // BLSF: Positions the rectangle against the long side of a free rectangle into which it fits the best.
        BestLongSideFit,
        // BAF: Positions the rectangle into the smallest free rect into which it fits.
        BestAreaFit,
        // BL: Does the Tetris placement.
        BottomLeftRule,
        // CP: Choosest the placement where the rectangle touches other rects as much as possible.
        ContactPointRule
    }

    class RectComparator implements Comparator<MultiScaleTexturePacker.Rect> {
        public int compare(MultiScaleTexturePacker.Rect o1, MultiScaleTexturePacker.Rect o2) {
            return o1.source.compareTo(o2.source);
        }
    }
}
