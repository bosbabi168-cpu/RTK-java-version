SeamstressNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual", "Hapus ukiran barang cashshop"}
		local buyopts = {
			"Peasant clothes",
			"Rogue's clothes",
			"Mage's dress",
			"Mage's skirt",
			"Poet's draperies",
			"Barang lainnya"
		}

		if npc.mapTitle == "Blossom Seams" then
			table.insert(opts, "Commission a job 2")
			table.insert(opts, "Suit of invisibility")
		end

		if npc.mapTitle == "Lin Cloth" then
			table.insert(opts, "Keahlian Kerajinan")
			table.insert(opts, "Seni Menjahit")
			table.insert(opts, "Tailoring Devotion")
			table.insert(opts, "Cloth Preparation")
		end

		local choice

		local pclothes = {
			Item("spring_dress").id,
			Item("spring_blouse").id,
			Item("spring_skirt").id,
			Item("spring_gown").id
		}
		local rclothes = {
			Item("summer_blouse").id,
			Item("autumn_blouse").id,
			Item("winter_blouse").id,
			Item("leather_blouse").id,
			Item("ancient_blouse").id,
			Item("earth_blouse").id
		}
		local mdress = {
			Item("summer_dress").id,
			Item("autumn_dress").id,
			Item("winter_dress").id,
			Item("leather_dress").id,
			Item("ancient_dress").id,
			Item("earth_dress").id
		}
		local mskirt = {
			Item("summer_skirt").id,
			Item("autumn_skirt").id,
			Item("winter_skirt").id,
			Item("leather_skirt").id,
			Item("heart_skirt").id,
			Item("earth_skirt").id
		}
		local pdraperies = {
			Item("summer_drapery").id,
			Item("autumn_drapery").id,
			Item("winter_drapery").id,
			Item("leather_drapery").id,
			Item("ancient_drapery").id,
			Item("earth_drapery").id
		}
		local pgowns = {
			Item("summer_gown").id,
			Item("autumn_gown").id,
			Item("winter_gown").id,
			Item("leather_gown").id,
			Item("ancient_gown").id,
			Item("earth_gown").id
		}
		local oitems = {Item("wedding_dress").id}

		local menu = player:menuString(
			"Halo! Apa yang ingin kau lakukan hari ini?",
			opts
		)

		if menu == "Beli" then
			choice = player:menuString(
				"Apa yang ingin kau beli hari ini?",
				buyopts,
				{}
			)

			local choice2 = {}

			if choice == "Peasant clothes" then
				choice2 = pclothes
			elseif choice == "Rogue's clothes" then
				choice2 = rclothes
			elseif choice == "Mage's dress" then
				choice2 = mdress
			elseif choice == "Mage's skirt" then
				choice2 = mskirt
			elseif choice == "Poet's gown" then
				choice2 = pgowns
			elseif choice == "Poet's draperies" then
				choice2 = pdraperies
			elseif choice == "Barang lainnya" then
				choice2 = oitems
			end

			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				choice2
			)
		elseif menu == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				SeamstressNpc.sellItems()
			)

			--[[ this is a subpath quest and part of an event in tk that is not active here
	elseif menu == "Commission a job 2" then
	elseif menu == "Suit of invisibility" then
		player:dialogSeq({t,"Hello there! I think I misheard you, did you actually you're looking for a suit of..invisibility?!?!...",
				"Oh my, I wouldn't know where to begin!",
				"*You tell "..npc.name.." the steps that Treilsaare taught you.*"},1)
		player:dialogSeq({t,"But you don't have the items I need.."},1)
		]]
			--
		elseif menu == "Keahlian Kerajinan" then
			generalNPC.crafting_skills(player, npc)
		elseif menu == "Seni Menjahit" then
			player:dialogSeq(
				{
					t,
					"Jadi kau merasa cocok jadi penjahit? Menjahit diperlukan untuk membuat busana jenis apa pun.",
					"Banyak jenis pakaian bisa dibuat penjahit seorang diri, meski sebagian butuh bantuan ahli logam.",
					"Ketika kau mencoba membuat busana, mutu hasilnya ((misalnya Spring, Summer, dsb.)) bergantung pada keahlianmu, kainmu, dan keberuntunganmu.",
					"Katakan 'penjahit' padaku bila kau siap membuat sesuatu. Aku juga bisa membantumu menyiapkan kain, yang dibutuhkan sebelum kau bisa membuat zirah."
				},
				0
			)
			return
		elseif menu == "Tailoring Devotion" then
			SeamstressNpc.tailoringDevotion(player, npc)
		elseif menu == "Cloth Preparation" then
			SeamstressNpc.clothPreparation(player, npc)
		end
	end),

	tailoringDevotion = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if crafting.checkSkillLegend(player, "tailoring") then
			player:dialogSeq(
				{
					t,
					"Kau sudah menekuni ilmu Tailoring."
				},
				0
			)
			return
		end

		crafting.checkSkill(player, npc, "woodworking")
		crafting.checkSkill(player, npc, "jewelry making")
		crafting.checkSkill(player, npc, "metalworking")

		player:dialogSeq(
			{
				t,
				"Penjahit bisa membuat pakaian, dan dengan bantuan ahli logam bisa membuat zirah. Kau ingin menjadi penjahit?"
			},
			1
		)

		crafting.addSkill(player, npc, "tailoring")
	end,

	clothPreparation = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		local tcloth = {graphic = convertGraphic(1632, "item"), color = 0}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if not crafting.checkSkillLegend(player, "tailoring") then
			player:dialogSeq({t, "Kau bukan penjahit."}, 0)
			return
		end

		if os.time() > player.quest["tailor_cloth_prepared"] then
			if player:hasItem("cloth", 2) ~= true then
				player:dialogSeq({tcloth, "Kau butuh dua helai kain."}, 0)
				return
			end

			player:removeItem("cloth", 2)
			player.quest["tailor_cloth_prepared"] = os.time() + 3600

			-- 1 hr
			player:dialogSeq(
				{
					tcloth,
					"Seluruh persiapan yang diperlukan sudah selesai. Kau masih harus menuntaskan tugasnya dalam satu jam ke depan."
				},
				0
			)
		end

		if os.time() < player.quest["tailor_cloth_prepared"] then
			player:dialogSeq(
				{
					t,
					"Kau sudah menyiapkan kain; pakai dulu yang itu."
				},
				0
			)
			return
		end
	end,

	onSayClick = async(function(player, npc, speech)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local speech = string.lower(player.speech)

		if speech == "acara khusus" and npc.mapTitle == "Lin Cloth" and player.quest[
			"spy_trials"
		] == 4 then
			local choices = {
				"Supaya penampilanku mewah",
				"Supaya hangat menembus angin malam yang dingin",
				"Sesuatu yang ringan dan bisa diwarnai"
			}
			local choice = player:menuSeq(
				"Hei sayang, untuk acara macam apa kau ingin berbusana?",
				choices,
				{}
			)
			if choice == 1 then
				if player.sex == 0 then
					player:addItem("farmer_clothes", 1)
				else
					player:addItem("spring_dress", 1)
				end
				player:dialogSeq({t, "Ini dia!"}, 0)
			elseif choice == 2 then
				player.quest["spy_trial_outfit_timer"] = os.time()
				player.quest["spy_trials"] = 5
				player:dialogSeq(
					{
						t,
						"Kenapa tidak bilang dari tadi! Tadi ada orang lain ke sini dengan permintaan serupa.",
						"** Penjahit itu keluar dari balik meja, mengunci pintu, lalu kembali membawa pita ukur **",
						"Ke kasino, ya? Kerumunan yang buruk. Para punggawa Kekaisaran itu selalu mengancam membongkar seluruh kedok kami di Sanhae",
						"Guild sudah mengamankan lowongan pelayan makanan tambahan di kasino bawah tanah, dan itulah kedok sementaramu untuk tugas ini",
						"Baik! Ukurannya sudah lengkap. Busanamu siap dalam dua hari. Datanglah lagi lalu, dan bawakan aku sup enak dari Kedai Koguryo itu!"
					},
					0
				)
			elseif choice == 3 then
				if player.sex == 0 then
					player:addItem("farmer_clothes", 1)
				else
					player:addItem("spring_dress", 1)
				end
				player:dialogSeq({t, "Ini dia!"}, 0)
			end
		elseif speech == "acara khusus" and npc.mapTitle == "Lin Cloth" and player.quest[
			"spy_trials"
		] == 5 then
			if os.time() > player.quest["spy_trial_outfit_timer"] + 60 then
				player.quest["spy_trials"] = 6
				player:dialogSeq(
					{
						t,
						"Informan Guild mengabarkan bahwa Kasino Bawah Tanah berikutnya akan digelar di bawah Pasar Nagnang.",
						"Kau harus ke sana dan menanyakan Penawaran Khusus kepada pengelola pasar.",
						"Hati-hati, tetapi kalau kau mengerjakannya dengan baik, aku yakin kau akan mendapat pekerjaan yang lebih tetap bersama kami."
					},
					0
				)
			else
				player:dialogSeq(
					{t, "Belum sepenuhnya siap. Datanglah lagi nanti."},
					0
				)
			end
		end

		if speech == "penjahit" then
			if npc.mapTitle == "Blossom Seams" or npc.mapTitle == "Lin Cloth" then
				crafting.craftingDialog(player, npc, speech)
			end
		end

		if speech == "siapkan" then
			if npc.mapTitle == "Blossom Seams" or npc.mapTitle == "Lin Cloth" then
				SeamstressNpc.clothPreparation(player, npc)
			end
		end
	end),

	buyItems = function()
		local buyItems = {}

		local pclothes = {
			Item("spring_dress").id,
			Item("spring_blouse").id,
			Item("spring_skirt").id,
			Item("spring_gown").id
		}
		local rclothes = {
			Item("summer_blouse").id,
			Item("autumn_blouse").id,
			Item("winter_blouse").id,
			Item("leather_blouse").id,
			Item("ancient_blouse").id,
			Item("earth_blouse").id
		}
		local mdress = {
			Item("summer_dress").id,
			Item("autumn_dress").id,
			Item("winter_dress").id,
			Item("leather_dress").id,
			Item("ancient_dress").id,
			Item("earth_dress").id
		}
		local mskirt = {
			Item("summer_skirt").id,
			Item("autumn_skirt").id,
			Item("winter_skirt").id,
			Item("leather_skirt").id,
			Item("heart_skirt").id,
			Item("earth_skirt").id
		}
		local pdraperies = {
			Item("summer_drapery").id,
			Item("autumn_drapery").id,
			Item("winter_drapery").id,
			Item("leather_drapery").id,
			Item("ancient_drapery").id,
			Item("earth_drapery").id
		}
		local pgowns = {
			Item("summer_gown").id,
			Item("autumn_gown").id,
			Item("winter_gown").id,
			Item("leather_gown").id,
			Item("ancient_gown").id,
			Item("earth_gown").id
		}
		local oitems = {Item("wedding_dress").id}

		for i = 1, #pclothes do
			table.insert(buyItems, pclothes[i])
		end
		for i = 1, #rclothes do
			table.insert(buyItems, rclothes[i])
		end
		for i = 1, #mdress do
			table.insert(buyItems, mdress[i])
		end
		for i = 1, #mskirt do
			table.insert(buyItems, mskirt[i])
		end
		for i = 1, #pdraperies do
			table.insert(buyItems, pdraperies[i])
		end
		for i = 1, #pgowns do
			table.insert(buyItems, pgowns[i])
		end
		for i = 1, #oitems do
			table.insert(buyItems, oitems[i])
		end

		return buyItems
	end,

	sellItems = function()
		local sellItems = SeamstressNpc.buyItems()

		return sellItems
	end
}
