package com.srizwan.task

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

/*
 * ---------------------------------------------------------------------------
 *  "টাস্ক" tab  (React: Task.jsx pTask + task/Dashboard.jsx + task/BookGrid.jsx)
 *   - daily goal / streak / badges / continue button
 *   - search + refresh
 *   - book cards with cover, progress bar, rate
 * ---------------------------------------------------------------------------
 */
@Composable
fun BooksTab(
    vm: TaskViewModel,
    listState: LazyListState,
    onOpenBook: (bookId: String, stem: String) -> Unit
) {
    val q = vm.query.trim().lowercase()
    val list = remember(vm.books, q) {
        vm.books.filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.author.lowercase().contains(q)
        }
    }
    val skeleton = vm.books.isEmpty() && vm.booksState == LoadState.Loading
    val failed = vm.books.isEmpty() && vm.booksState == LoadState.Error
    val showEmpty = !skeleton && (failed || list.isEmpty())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "dash") { DashboardCard(vm, onOpenBook) }

        item(key = "search") { SearchRow(vm) }

        if (vm.spinning) {
            item(key = "spin") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (skeleton) {
            items(4) { BookSkeleton() }
        } else {
            items(list, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    done = vm.bookCounts[book.id] ?: 0,
                    mine = vm.summary?.byBook?.get(book.id)?.n ?: 0,
                    rate = vm.rateOfBook(book),
                    isLast = vm.resume?.first == book.id,
                    onClick = { onOpenBook(book.id, "") }
                )
            }
        }

        if (showEmpty) {
            item(key = "empty") {
                EmptyNote(
                    if (failed) "কিতাব লোড হয়নি — নেট দেখে রিফ্রেশ চাপুন"
                    else "কোনো কিতাব পাওয়া যায়নি"
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search + refresh
// ---------------------------------------------------------------------------
@Composable
private fun SearchRow(vm: TaskViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("কিতাব বা লেখকের নাম খুঁজুন", fontSize = 14.sp) },
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            visualTransformation = AppFontTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        IconPill(icon = "🔄", onClick = { vm.refreshAll() })
    }
}

// ---------------------------------------------------------------------------
// Dashboard: goal, streak, badges, continue
// ---------------------------------------------------------------------------
@Composable
private fun DashboardCard(
    vm: TaskViewModel,
    onOpenBook: (bookId: String, stem: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val rt = resumeTarget(vm.resume, vm.booksById, vm.proofs)
    val hasStat = vm.finReady && hasDates(vm.proofs)
    if (!hasStat && rt == null) return

    SoftCard {
        if (hasStat) {
            val goal = vm.goal
            val s = remember(vm.proofs) { streakInfo(vm.proofs) }
            val left = maxOf(0, goal - s.today)
            val hit = s.today >= goal
            val pct = minOf(1f, s.today.toFloat() / goal)
            val next = BADGES.firstOrNull { it.first > s.best }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // daily goal (tap to change)
                Surface(
                    onClick = { vm.openGoalDialog() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = (if (hit) OkColor else colors.primary).copy(alpha = 0.12f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("🎯 আজকের লক্ষ্য", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        Text(
                            "${bn(s.today)} / ${bn(goal)} পেইজ",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        ProgressBar(pct, color = if (hit) OkColor else colors.primary, height = 6.dp)
                        Text(
                            (if (hit) "লক্ষ্য পূরণ হয়েছে ✓" else "আর ${bn(left)} পেইজ বাকি") + " • বদলাতে চাপুন",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                // streak
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = GoldColor.copy(alpha = 0.14f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("⚡ টানা কাজ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldColor)
                        Text(
                            "${bn(s.current)} দিন",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            (if (s.doneToday) "আজকের দিন গোনা হয়েছে"
                            else if (s.current > 0) "আজ কাজ করলে ${bn(s.current + 1)} দিন হবে"
                            else "আজ একটি পেইজ করলেই শুরু") + " • সেরা ${bn(s.best)} দিন",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // badges
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BADGES.forEach { (days, name) ->
                    val on = s.best >= days
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (on) GoldColor.copy(alpha = 0.22f) else colors.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            "🏆 $name",
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) GoldColor else colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            if (next != null) {
                Hint("পরের ব্যাজ \"${next.second}\": টানা ${bn(next.first)} দিন (সেরা রেকর্ড থেকে আরও ${bn(next.first - s.best)} দিন)।")
            }
        }

        if (rt != null) {
            Button(
                onClick = { onOpenBook(rt.book.id, rt.stem) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "▶ চালিয়ে যান — ${rt.book.name}" + if (rt.stem.isNotBlank()) " • পেইজ ${rt.stem}" else "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Book card
// ---------------------------------------------------------------------------
@Composable
private fun BookCard(
    book: Book,
    done: Int,
    mine: Int,
    rate: Double,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 102.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
            ) {
                BookCover(book.coverUrl)
                if (isLast) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        shape = RoundedCornerShape(50),
                        color = GoldColor
                    ) {
                        Text(
                            "শেষ কাজ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    book.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // progress: done / remaining
                val total = book.totalPages
                if (total > 0) {
                    ProgressBar(clampPct(done, total), height = 6.dp)
                    Text(
                        "${bn(done)} সম্পন্ন / ${bn(maxOf(0, total - done))} বাকি",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                } else if (done > 0) {
                    Text(
                        "${bn(done)} সম্পন্ন / বাকি অজানা",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Text(
                    "রেট ${rateTxt(rate)}/পেইজ" + if (mine > 0) " • আমার ${bn(mine)}" else "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
            }
        }
    }
}

@Composable
private fun BookCover(url: String) {
    val placeholder: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("📖", fontSize = 28.sp)
        }
    }
    if (url.isBlank()) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() }
        )
    }
}

@Composable
private fun BookSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(Modifier.size(width = 74.dp, height = 102.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.width(180.dp).height(16.dp))
                SkeletonBox(Modifier.width(110.dp).height(12.dp))
                SkeletonBox(Modifier.width(200.dp).height(8.dp))
                SkeletonBox(Modifier.width(140.dp).height(12.dp))
            }
        }
    }
}
