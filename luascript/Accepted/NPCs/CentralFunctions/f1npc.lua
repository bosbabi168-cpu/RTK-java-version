F1Npc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.lastClick = npc.ID
		player.dialogType = 0

		local opts = {}

		local string = "Hello " .. player.name .. "! How can I help you today?"

		if player.gmLevel >= 99 then
			table.insert(opts, "GM Menu")
			string = string .. "\n\nMap ID: " .. player.m .. " X: " .. player.x .. " Y: " .. player.y
		end

		table.insert(opts, "Silver Thread")

		--table.insert(opts,"Wisdom Star Status")
		table.insert(opts, "Minigame Stats")
		table.insert(opts, "Mantra")
		table.insert(opts, "Toggles")
		table.insert(opts, "Halaman web karakterku")
		table.insert(opts, "Character Stats")
		table.insert(opts, "Faerie Light")
		table.insert(opts, "AFK Message")
		table.insert(opts, "Kan account information")

		if PoemNpc.checkPlayerSelectionList(player) == true then
			table.insert(opts, "Pindah ke Ruang Pemilihan Puisi")
		end

		if player:hasLegend("head_tutor") or player.gmLevel > 0 then
			table.insert(opts, "Tutor Management")
		end

		if player.tutor == 1 or player.gmLevel > 0 then
			table.insert(opts, "Pergi ke Tutor Haven")
			table.insert(opts, "Novice Listener")
		end

		if player.class == 0 and player.level >= 5 then
			table.insert(opts, "Choose a path")
		end

		table.insert(opts, "Recover Death Pile")

		local choice = player:menuString(string, opts, {})

		if choice == "Silver Thread" then
			local warpChoice

			if player.m == 666 then
				return
			end

			if player.m >= 4711 and player.m <= 4718 then
				general_npc_funcs.reincarnate(player)
				return
			end

			if player.state ~= 1 then
				player:dialogSeq(
					{
						t,
						"Ini untuk arwah negeri ini menemukan jalan menuju dukun. Kau belum mati, jadi tidak ada jalan bagimu di sini."
					},
					0
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Ah, satu lagi yang berjalan di antara barisan orang mati... tetapi waktumu belum tiba... akan kuberi jalan menuju Dukun agar kau hidup kembali."
				},
				1
			)

			if player.m >= 59000 and player.m <= 65000 then
				general_npc_funcs.reincarnate(player)
				player:warp(4259, 11, 14)
				return
			end

			if player.country == 0 then
				--wilderness
				warpChoice = player:menuString(
					"Dukun mana yang ingin kau kunjungi?",
					{
						"Pendeta Hyun Moo, di sisi Utara Wilderness",
						"Pendeta Ju Jak, di sisi Selatan Wilderness",
						"Pendeta Baekho, di sisi Barat Wilderness",
						"Pendeta Chung ryong, di sisi Timur Wilderness"
					},
					{}
				)
			elseif player.country == 1 then
				--kugnae
				warpChoice = player:menuString(
					"Dukun mana yang ingin kau kunjungi?",
					{
						"Dusk, di sebelah Barat Kugnae.",
						"Dawn, di sebelah Timur Kugnae."
					},
					{}
				)
			elseif player.country == 2 then
				-- buya
				warpChoice = player:menuString(
					"Dukun mana yang ingin kau kunjungi?",
					{
						"Felis, di sebelah Barat Buya.",
						"Storm, di sebelah Timur Buya."
					},
					{}
				)
			end

			if warpChoice == "Pendeta Hyun Moo, di sisi Utara Wilderness" then
				player:warp(1416, 11, 5)
			elseif warpChoice == "Pendeta Ju Jak, di sisi Selatan Wilderness" then
				player:warp(1411, 11, 5)
			elseif warpChoice == "Pendeta Baekho, di sisi Barat Wilderness" then
				player:warp(1406, 11, 5)
			elseif warpChoice == "Pendeta Chung ryong, di sisi Timur Wilderness" then
				player:warp(1401, 11, 5)
			elseif warpChoice == "Dusk, di sebelah Barat Kugnae." then
				player:warp(8, 6, 4)
			elseif warpChoice == "Dawn, di sebelah Timur Kugnae." then
				player:warp(9, 3, 5)
			elseif warpChoice == "Felis, di sebelah Barat Buya." then
				player:warp(338, 4, 4)
			elseif warpChoice == "Storm, di sebelah Timur Buya." then
				player:warp(339, 3, 5)
			end
		elseif choice == "GM Menu" then
			menu = player:menuString(
				"<b>[Menu GM]\n\nApa yang ingin kau lakukan?",
				{
					"God Tools",
					"Private Tools",
					"Minigame Powers",
					"System Broadcast",
					"Wisdom Star Tools"
				}
			)
			if menu == "God Tools" then
				god_tools.f1click(player, npc)
			elseif menu == "Private Tools" then
				private_tools.click(player, npc)
			elseif menu == "Minigame Powers" then
				minigame_powers.f1click(player, npc)
			elseif menu == "System Broadcast" then
				local input = player:input("Masukkan pesan yang ingin kau siarkan")
				gmbroadcast(-1, "[SYSTEM]: " .. input)
			elseif menu == "Wisdom Star Tools" then
				local subChoices = {
					"Extend Wisdom Star",
					"Set Minutes Remaining",
					"Set Multiplier",
					"Set Total Points"
				}

				local subChoice = player:menuString("Select option", subChoices)
				if subChoice == "Extend Wisdom Star" then
					local minutes = player:inputNumberCheck(player:input("Ingin diperpanjang berapa menit?"))

					local seconds = minutes * 60

					core.gameRegistry["wisdom_star_timer"] = core.gameRegistry[
						"wisdom_star_timer"
					] + seconds

					broadcast(
						-1,
						"Praise the Gods! GM " .. player.name .. " has manually extended Wisdom star by " .. minutes .. " minutes."
					)
				elseif subChoice == "Set Minutes Remaining" then
					local minutes = player:inputNumberCheck(player:input("Ingin diatur berapa menit?"))
					local seconds = minutes * 60

					core.gameRegistry["wisdom_star_timer"] = os.time() + seconds

					player:dialogSeq({t, "Sudah selesai."}, 0)
				elseif subChoice == "Set Multiplier" then
					local multiplier = player:input("Pengalinya mau diatur berapa (jangan masukkan x)?")

					if tonumber(multiplier) < 1 then
						return
					end

					setWisdomStarMultiplier(multiplier)

					broadcast(
						-1,
						"Praise the Gods! GM " .. player.name .. " has set the Wisdom Star multiplier to " .. multiplier .. "x!"
					)
				elseif subChoice == "Set Total Points" then
					local points = player:input("Angka wisdom star harian mau diatur berapa?")

					if tonumber(points) < 0 then
						return
					end

					setKanDonationPoints(points)
				end
			end
		elseif choice == "Mantra" then
			local menuChoice = player:menuString(
				"Pilih satu pilihan.",
				{"Learnable/Divine Secret"}
			)

			if menuChoice == "Learnable/Divine Secret" then
				player:currentFutureSpells(npc)
			end
		elseif choice == "Toggles" then
			local s = "buffer"
			while s ~= "nothing" do
				local menuChoice = player:menuString(
					"Pilih daftar saklar yang ingin kau ubah.",
					{"Chat Toggles", "Misc Toggles"},
					{}
				)

				if menuChoice == "Chat Toggles" then
					local chats = {"Clan Chat", "SubPath Chat"}
					local currentStatus = {player.clanChat, player.subpathChat}
					local chatsS = {}

					for i = 1, #chats do
						status = ""
						if currentStatus[i] == 0 then
							status = "Off"
						elseif currentStatus[i] == 1 then
							status = "On"
						end

						table.insert(chatsS, chats[i] .. ": " .. status)
					end

					local choice = player:menuSeq(
						"Pilih satu saklar untuk menyalakan atau mematikannya.",
						chatsS,
						{}
					)

					if choice == 1 then
						if currentStatus[choice] == 0 then
							player.clanChat = 1
							player:sendMinitext("Obrolan Klan: NYALA")
						end
						if currentStatus[choice] == 1 then
							player.clanChat = 0
							player:sendMinitext("Clan Chat: OFF")
						end
					elseif choice == 2 then
						if currentStatus[choice] == 0 then
							player.subpathChat = 1
							player:sendMinitext("Obrolan Subjalur: NYALA")
						end
						if currentStatus[choice] == 1 then
							player.subpathChat = 0
							player:sendMinitext("Subpath Chat: OFF")
						end
					end
				elseif menuChoice == "Misc Toggles" then
					local toggles = {"See Warps", "Disable Experience Gain"}
					local currentStatus = {
						player.registry["see_warps"],
						player.registry["disableExperienceGain"]
					}
					local toggleS = {}
					local status

					for i = 1, #toggles do
						status = ""
						if currentStatus[i] == 0 then
							status = "Off"
						elseif currentStatus[i] == 1 then
							status = "On"
						end

						table.insert(toggleS, toggles[i] .. ": " .. status)
					end

					local toggleChoice = player:menuSeq(
						"Pilih satu saklar untuk menyalakan atau mematikannya.",
						toggleS,
						{}
					)

					if toggleChoice == 1 then
						if currentStatus[toggleChoice] == 0 then
							player.registry["see_warps"] = 1
							player:sendMinitext("Lihat Portal: NYALA")
						end

						if currentStatus[toggleChoice] == 1 then
							player.registry["see_warps"] = 0
							player:sendMinitext("Lihat Portal: MATI")
						end
					elseif toggleChoice == 2 then
						if currentStatus[toggleChoice] == 0 then
							player.registry["disableExperienceGain"] = 1
							player:sendMinitext("Matikan Perolehan Exp: NYALA")
						end

						if currentStatus[toggleChoice] == 1 then
							player.registry["disableExperienceGain"] = 0
							player:sendMinitext("Disable Exp Gain: OFF")
						end
					end
				end
			end
		elseif choice == "Choose a path" then
			F1Npc.level5popupDialog(player)
		elseif choice == "Recover Death Pile" then
			local deathPileFound = 0
			local deathPile = player:getObjectsInArea(BL_ITEM)

			--added 4/2/17 for death pile recovery
			if #deathPile > 0 then
				for i = 1, #deathPile do
					if distanceSquare(player, deathPile[i], 3) then
						if player:isYours(deathPile[i]) then
							deathPileFound = 1
						end
					end
				end
			end

			if deathPileFound == 0 then
				player:dialogSeq(
					{
						t,
						"Kemampuan ini memungkinkanmu mengambil kembali barang yang hilang saat pemain nakal berdiri di atasnya. Untuk memakainya, kau harus menghadap barang yang kau jatuhkan ketika mati, dan berjarak hanya satu atau dua langkah darinya.",
						"Lalu tekan F1 dan pilih \"Recover Death Pile\". Barangmu akan kembali walaupun calon pencuri berdiri di atasnya! Untuk memakai kemampuan ini kau harus dalam keadaan hidup. Kalau ruang kantongmu tidak cukup, tidak semua barangmu bisa diambil kembali."
					},
					0
				)
			elseif deathPileFound == 1 then
				if player.state == 1 then
					player:dialogSeq(
						{
							t,
							"Kau tidak bisa mengambil tumpukan barang matimu selagi kau mati."
						},
						0
					)
					return
				end
				player:recoverDeathPile()
			end
		elseif choice == "Character Stats" then
			local string = "<b>" .. player.name .. "\n\n<b>[Base Stats]\nAC: " .. player.baseArmor .. "\nMight: " .. player.baseMight .. " | Will: " .. player.baseWill .. " | Grace: " .. player.baseGrace .. "\nVita: " .. Tools.formatNumber(player.baseHealth) .. " | Mana: " .. Tools.formatNumber(player.baseMagic) .. "\n"

			string = string .. "\n"
			string = string .. "<b>[Equipped Stats]\nAC: " .. player.armor .. "\nMight: " .. player.might .. " | Will: " .. player.will .. " | Grace: " .. player.grace .. "\nVita: " .. Tools.formatNumber(player.health) .. " | Mana: " .. Tools.formatNumber(player.magic) .. "\n"

			local alignment = "Natural"

			if player.alignment == 1 then
				alignment = "Kwi-Sin"
			elseif player.alignment == 2 then
				alignment = "Ming-Ken"
			elseif player.alignment == 3 then
				alignment = "Ohaeng"
			end

			string = string .. "\nAlignment: " .. alignment .. "\n"

			string = string .. "\nTotal Exp sold: " .. Tools.formatNumber(player.expSoldHealth + player.expSoldMagic + player.expSoldStats)

			player:popUp(string)
		elseif choice == "Faerie Light" then
			player:faerieLight()
		elseif choice == "Halaman web karakterku" then
			local choice = player:menuString(
				"Apa yang ingin kau lakukan dengan halaman pengguna RTK-mu?",
				{"Change", "Remove", "Bantuan"}
			)

			if choice == "Change" then
				local s = "buffer"

				while s == "buffer" do
					local opts = {
						"Show Vita Statistics",
						"Show Equipment List",
						"Show Legend",
						"Show Spells",
						"Show Inventory",
						"Show Banked Items"
					}
					local currentOptStatus = {
						player.profileVitaStats,
						player.profileEquipList,
						player.profileLegends,
						player.profileSpells,
						player.profileInventory,
						player.profileBankItems
					}

					local optsS = {}
					local status = ""

					for i = 1, #opts do
						if currentOptStatus[i] == 0 then
							status = "Disable"
						elseif currentOptStatus[i] ~= 0 then
							status = "Enable"
						end

						table.insert(optsS, opts[i] .. ": " .. status)
					end

					local optChoice = player:menuSeq(
						"Pilih bagian profilmu yang ingin kau nyalakan atau matikan.",
						optsS,
						{}
					)

					if optChoice == 1 then
						if currentOptStatus[optChoice] == 0 then
							player.profileVitaStats = 1
							player:sendMinitext(opts[optChoice] .. ": ENABLE")
						else
							player.profileVitaStats = 0
							player:sendMinitext(opts[optChoice] .. ": DISABLE")
						end
					elseif optChoice == 2 then
						if currentOptStatus[optChoice] == 0 then
							player.profileEquipList = 1
							player:sendMinitext(opts[optChoice] .. ": ENABLE")
						else
							player.profileEquipList = 0
							player:sendMinitext(opts[optChoice] .. ": DISABLE")
						end
					elseif optChoice == 3 then
						if currentOptStatus[optChoice] == 0 then
							player.profileLegends = 1
							player:sendMinitext(opts[optChoice] .. ": ENABLE")
						else
							player.profileLegends = 0
							player:sendMinitext(opts[optChoice] .. ": DISABLE")
						end
					elseif optChoice == 4 then
						if currentOptStatus[optChoice] == 0 then
							player.profileSpells = 1
							player:sendMinitext(opts[optChoice] .. ": ENABLE")
						else
							player.profileSpells = 0
							player:sendMinitext(opts[optChoice] .. ": DISABLE")
						end
					elseif optChoice == 5 then
						if currentOptStatus[optChoice] == 0 then
							player.profileInventory = 1
							player:sendMinitext(opts[optChoice] .. ": ENABLE")
						else
							player.profileInventory = 0
							player:sendMinitext(opts[optChoice] .. ": DISABLE")
						end
					elseif optChoice == 6 then
						if currentOptStatus[optChoice] == 0 then
							player.profileBankItems = 1
							player:sendMinitext(opts[optChoice] .. ": ENABLE")
						else
							player.profileBankItems = 0
							player:sendMinitext(opts[optChoice] .. ": DISABLE")
						end
					end
				end
			elseif choice == "Remove" then
				local confirm = player:menuSeq(
					"Kau yakin ingin mematikan profilmu? (Ini mematikan seluruh saklarnya satu per satu.)",
					{"Matikan profilku.", "Nevermind."},
					{}
				)

				if confirm == 1 then
					player.profileVitaStats = 0
					player.profileEquipList = 0
					player.profileLegends = 0
					player.profileSpells = 0
					player.profileInventory = 0
					player.profileBankItems = 0

					player:dialogSeq(
						{
							t,
							"Profilmu sudah dimatikan. Sekarang kau hanya menampilkan keterangan dasar yang ditampilkan semua orang."
						},
						0
					)
					return
				end
			elseif choice == "Bantuan" then
				player:dialogSeq(
					{
						t,
						"Fitur ini memungkinkanmu mengatur sendiri pilihan halaman pengguna RTK (https://users.RetroTK.com)",
						"Kau bisa memilih keterangan apa yang ingin ditampilkan di situs itu, serta beberapa pilihan untuk gambar karaktermu.",
						"Halaman-halaman itu diperbarui secara langsung dengan keterangan yang diambil dari permainan.",
						"Ingat, kenakan sesuatu yang bagus, sebab itulah yang tampil di situs."
					},
					0
				)
				return
			end
		elseif choice == "Wisdom Star Status" then
			---- WISDOM STAR STATUS-----

			local status
			local multiplier = 0
			local timeRemaining = (os.time() - core.gameRegistry["wisdom_star_timer"])
			local lapisAmount = 0

			local wisdomStarMultiplier = getWisdomStarMultiplier()

			if wisdomStarMultiplier < 1 then
				wisdomStarMultiplier = 1
			end

			wisdomStarMultiplier = string.format("%.2f", wisdomStarMultiplier)

			if core.gameRegistry["wisdom_star"] == 0 then
				status = "OFF"
				timeRemaining = 0
			else
				status = "ON"
			end

			string = "<b>[WISDOM STAR]\n\nStatus: " .. status .. " | " .. wisdomStarMultiplier .. "x EXP\n\nTime remaining: " .. getTimerValues("wisdom_star_timer") .. "\n\nCurrent Total: " .. Tools.formatNumber(getKanDonationPoints())

			local choice2 = player:menuString(string, {"Donate", "Keluar"}, {})

			if choice2 == "Donate" then
				if player.actId == 0 then
					-- not registered
					player:dialogSeq(
						{
							t,
							"Karaktermu harus terdaftar (terhubung ke akun) untuk bisa menyumbang."
						},
						0
					)
					return
				end

				local amount = player:inputNumberCheck(player:input("Berapa banyak yang ingin kau sumbangkan?\n\nSaldo Kan: " .. Tools.formatNumber(player.registry["kan"])))

				if amount <= 0 then
					player:dialogSeq(
						{
							t,
							"Kau harus memasukkan angka positif yang tidak melebihi saldo Kan-mu."
						},
						0
					)
					return
				end

				if amount > player.registry["kan"] then
					player:dialogSeq(
						{
							t,
							"Kau tidak bisa memasukkan Kan melebihi saldo Kan-mu saat ini."
						},
						0
					)
					return
				end

				local choice3 = player:menuSeq(
					"Kau yakin ingin menyumbangkan " .. amount .. " Kan towards Wisdom Star?",
					{"Ya", "Tidak"},
					{}
				)

				if choice3 == 1 then
					KanNpc.removeKan(player, amount)
					wisdom_star.setPurchase(player, amount)
					player:dialogSeq(
						{
							t,
							"Terima kasih banyak atas sumbanganmu sebesar " .. Tools.formatNumber(amount) .. " Kan. Itu sangat berarti bagi kami dan sangat kami hargai."
						},
						0
					)
				elseif choice3 == 2 then
					player:dialogSeq({t, "Ah, terima kasih juga."}, 0)
					return
				end
			end

			------------------------------------
		elseif choice == "Minigame Stats" then
			local choices = {
				"Carnage",
				"Elixir",
				"Sumo war",
				"Beach war",
				"Bomber war"
			}

			local choice = player:menuString("Pilih satu permainan.", choices)

			if choice == "Carnage" then
				local totalGames = player.registry["carnagePart"]
				local victories = player.registry["carnageWin"]
				local losses = totalGames - victories
				local percentWin = (victories / totalGames) * 100

				local text = "<b>Carnage\n\nWon " .. string.format(
					"%.2f",
					percentWin
				) .. "% of total games played\n\nVictories: " .. victories .. "\nLosses: " .. losses .. "\nTotal games played: " .. totalGames
				player:dialogSeq({t, text}, 0)
			elseif choice == "Elixir" then
				local totalGames = player.registry[
					"participated_in_elixir_wars"
				]
				local victories = player.registry["elixir_war_victories"]
				local losses = totalGames - victories
				local percentWin = (victories / totalGames) * 100

				local text = "<b>Elixir\n\nWon " .. string.format(
					"%.2f",
					percentWin
				) .. "% of total games played\n\nVictories: " .. victories .. "\nLosses: " .. losses .. "\nTotal games played: " .. totalGames
				player:dialogSeq({t, text}, 0)
			elseif choice == "Sumo war" then
				local totalGames = player.registry["sumo_war_entries"]
				local victories = player.registry["sumo_war_wins"]
				local losses = totalGames - victories
				local percentWin = (victories / totalGames) * 100

				local text = "<b>Sumo war\n\nWon " .. string.format(
					"%.2f",
					percentWin
				) .. "% of total games played\n\nVictories: " .. victories .. "\nLosses: " .. losses .. "\nTotal games played: " .. totalGames
				player:dialogSeq({t, text}, 0)
			elseif choice == "Beach war" then
				local totalGames = player.registry["beach_war_entries"]
				local victories = player.registry["beach_war_wins"]
				local losses = totalGames - victories
				local percentWin = (victories / totalGames) * 100

				local text = "<b>Beach war\n\nWon " .. string.format(
					"%.2f",
					percentWin
				) .. "% of total games played\n\nVictories: " .. victories .. "\nLosses: " .. losses .. "\nTotal games played: " .. totalGames
				player:dialogSeq({t, text}, 0)
			elseif choice == "Bomber war" then
				local totalGames = player.registry["bomber_war_entries"]
				local victories = player.registry["bomber_war_wins"]
				local losses = totalGames - victories
				local percentWin = (victories / totalGames) * 100

				local text = "<b>Bomber war\n\nWon " .. string.format(
					"%.2f",
					percentWin
				) .. "% of total games played\n\nVictories: " .. victories .. "\nLosses: " .. losses .. "\nTotal games played: " .. totalGames
				player:dialogSeq({t, text}, 0)
			end
		elseif choice == "Pindah ke Ruang Pemilihan Puisi" then
			player:warp(4019, math.random(9, 12), math.random(15, 17))
		elseif choice == "AFK Message" then
			afkMessage = player:input("Current AFK Message: " .. player.afkMessage .. "\n\nTulis Pesan AFK-mu di bawah ini: ")
			player.afkMessage = afkMessage
			player:sendMinitext("AFK Message updated")
			player:updateState()
		elseif choice == "Kan account information" then
			KanNpc.getKanAccountInfo(player)
		elseif choice == "Pergi ke Tutor Haven" then
			if (not player:canCast(0, 1, 0)) then
				return
			end

			if player.m == 1228 then
				player:sendMinitext("Fizzle.")
				return
			end
			if (player.canSummon == 0) then
				player:sendMinitext("Fizzle.")
				return
			end
			if player.mapTitle == "Buya Kan Shop" or player.mapTitle == "Kugnae Kan Shop" then
				player:sendMinitext("Fizzle.")
				return
			end
			if (player.state == 1) then
				player:sendMinitext("Fizzle.")
				return
			end
			if player.warpOut == 0 then
				player:sendMinitext("Itu tidak berlaku di sini.")
				return
			end
			if player.m == 3010 or player.m == 3011 or player.m == 33 or player.m == 3017 then
				player:sendMinitext("Fizzle.")
				return
			end

			local warpConfirm = player:menuSeq(
				"Pindah ke Tutor's Haven?",
				{"Ya", "Tidak"},
				{}
			)

			if warpConfirm == 1 then
				player:playSound(4)
				player:sendAnimation(11)
				player:sendStatus()
				player:sendAction(6, 35)
				player:warp(3573, 10, 15)
			end
		elseif choice == "Novice Listener" then
			PathArenaTutorNpc.noviceListener(player, npc)
		elseif choice == "Tutor Management" then
			local choice = player:menuString(
				"Apa yang ingin kau lakukan?",
				{"Add tutor", "Remove tutor"}
			)

			if choice == "Add tutor" then
				local name = player:inputLetterCheck(player:input("Siapa yang ingin kau angkat sebagai tutor?"))
				local target = Player(name)

				if target == nil then
					player:dialogSeq({t, "Pemain tidak daring."}, 0)
					return
				end

				if target:hasLegend("tutor") then
					player:dialogSeq({t, "Pemain itu sudah menjadi tutor."}, 0)
					return
				end

				local classes = {"Warrior", "Rogue", "Mage", "Poet"}

				local classChoice = player:menuString(
					"Tutor jenis apa?",
					classes
				)

				target.tutor = 1
				target:addLegend(
					classChoice .. " Tutor (" .. curT() .. ")",
					"tutor",
					3,
					128
				)

				broadcast(
					-1,
					"[TUTOR]: " .. target.name .. " has been appointed by " .. player.name .. " as a " .. classChoice .. " Tutor!"
				)

				player:dialogSeq(
					{
						t,
						target.name .. " telah ditambahkan sebagai " .. classChoice .. " Tutor!"
					},
					0
				)
			elseif choice == "Remove tutor" then
				local name = player:inputLetterCheck(player:input("Siapa yang ingin kau lepas dari jabatan tutor?"))
				local target = Player(name)

				if target == nil then
					player:dialogSeq({t, "Pemain tidak daring."}, 0)
					return
				end

				if not target:hasLegend("tutor") then
					player:dialogSeq({t, "Pemain itu bukan tutor."}, 0)
					return
				end

				target.tutor = 0
				target:removeLegendbyName("tutor")

				player:dialogSeq(
					{t, target.name .. " telah dilepas dari jabatan tutor!"},
					0
				)
			end
		end
	end),

	level5popupDialog = function(player)
		local t = {graphic = convertGraphic(3, "monster"), color = 3}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local m = 0
		local x = 8
		local y = 7

		if (player.state == 1) then
			player:dialogSeq(
				{
					t,
					"Kau tidak bisa bepergian ke mana pun karena kau tidak hidup. Kunjungi Dukun lebih dulu."
				},
				1
			)
		end
		local guilds = {
			"Guild Prajurit",
			"Guild Rogue",
			"Guild Penyihir",
			"Guild Pujangga"
		}

		player:dialogSeq(
			{
				t,
				"Selamat! Kau masih muda tetapi pencerahanmu tumbuh setiap hari. Kini kau harus memilih jalur untuk melanjutkan perjalananmu."
			},
			1
		)

		local choice = player:menuSeq(
			"Pilih guild yang ingin kau kunjungi.",
			guilds,
			{}
		)

		if choice == 1 then
			-- warriors guild
			if player.country == 1 then
				-- Koguryo
				m = 11
			elseif player.country == 2 then
				-- Buya
				m = 341
			end
		elseif choice == 2 then
			-- rogues guild
			if player.country == 1 then
				m = 15
			elseif player.country == 2 then
				m = 343
			end
		elseif choice == 3 then
			-- mages guild
			if player.country == 1 then
				m = 13
			elseif player.country == 2 then
				m = 342
			end
		elseif choice == 4 then
			-- poet's guild
			if player.country == 1 then
				m = 17
			elseif player.country == 2 then
				m = 344
			end
		end

		if choice ~= nil then
			player:warp(m, x, y)
		end
	end
}
